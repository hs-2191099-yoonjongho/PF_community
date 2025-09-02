package com.example.community.service;

import com.example.community.common.FilePolicy;
import com.example.community.domain.PostImage;
import com.example.community.repository.PostImageRepository;
import com.example.community.service.dto.ImageMeta;
import com.example.community.service.exception.AccessDeniedException;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.storage.Storage;
import com.example.community.storage.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 파일 관리 서비스
 * 파일 업로드, 삭제 및 유효성 검사를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final Storage storage;
    private final PostImageRepository postImageRepository;
    
    /**
     * 게시글용 이미지 업로드 (키만 반환)
     * @param files 업로드할 이미지 파일 목록
     * @param memberId 업로드 요청자 ID
     * @return 업로드된 이미지 키 목록
     */
    public List<String> uploadPostImageKeys(List<MultipartFile> files, Long memberId) {
        // 파일 업로드 공통 로직 처리 (스토리지에 저장)
        List<Storage.StoredFile> storedFiles = uploadFilesToStorage(files, memberId);
        
        // 키만 추출하여 반환
        return storedFiles.stream()
                .map(Storage.StoredFile::key)
                .toList();
    }
    
    /**
     * 게시글용 이미지 업로드 (기존 메서드 유지)
     * @param files 업로드할 이미지 파일 목록
     * @param memberId 업로드 요청자 ID
     * @return 업로드된 이미지 메타데이터 목록
     * @deprecated 이미지 키만 반환하는 메서드 사용 권장: {@link #uploadPostImageKeys(List, Long)}
     */
    @Deprecated
    public List<ImageMeta> uploadPostImages(List<MultipartFile> files, Long memberId) {
        // 파일 업로드 공통 로직 처리 (스토리지에 저장)
        List<Storage.StoredFile> storedFiles = uploadFilesToStorage(files, memberId);
        
        // 메타데이터 생성하여 반환
        return storedFiles.stream()
                .map(stored -> new ImageMeta(stored.key(), stored.url()))
                .toList();
    }
    
    /**
     * 파일 업로드 공통 로직 (중복 제거를 위한 내부 메서드)
     * @param files 업로드할 파일 목록
     * @param memberId 업로드 요청자 ID
     * @return 저장된 파일 정보 목록
     */
    private List<Storage.StoredFile> uploadFilesToStorage(List<MultipartFile> files, Long memberId) {
        List<Storage.StoredFile> result = new ArrayList<>();
        
        // 전체 업로드 크기 체크
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (FilePolicy.isTotalSizeExceeded(totalSize)) {
            throw new IllegalArgumentException(
                String.format(FilePolicy.ERR_TOTAL_SIZE_EXCEEDED, FilePolicy.MAX_TOTAL_SIZE_BYTES));
        }
        
        for (MultipartFile file : files) {
            try {
                // 파일 유효성 검사
                validateImageFile(file);
                
                // 파일 저장 (멤버 ID를 포함한 키 생성)
                String fileName = generateUniqueFileName(file.getOriginalFilename());
                String fileKey = String.format("%s/%d/%s", FilePolicy.POST_IMAGES_PATH, memberId, fileName);
                
                // 경로 주입 공격 방지
                if (!FilePolicy.isPathSafe(fileKey)) {
                    throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
                }
                
                Storage.StoredFile stored = storage.storeWithKey(file, fileKey);
                result.add(stored);
                
                log.debug("이미지 업로드 성공: 회원={}, 파일명={}, 키={}", 
                        memberId, file.getOriginalFilename(), stored.key());
                
            } catch (Exception e) {
                log.error("이미지 업로드 실패: 회원={}, 파일명={}, 오류={}", 
                        memberId, file.getOriginalFilename(), e.getMessage());
                // 실패한 파일은 건너뛰고 계속 진행
            }
        }
        
        return result;
    }
    
    /**
     * 고유한 파일명 생성
     * UUID를 사용하여 중복을 방지합니다.
     */
    private String generateUniqueFileName(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // 암호학적으로 안전한 랜덤 파일명 생성
        byte[] randomBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(randomBytes);
        String randomId = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        
        return randomId + extension;
    }
    
    /**
     * 게시글 이미지 삭제
     * 요청자가 이미지 소유자인지 확인 후 삭제합니다.
     * 
     * @param key 삭제할 이미지 키
     * @param memberId 삭제 요청자 ID
     * @throws AccessDeniedException 이미지 소유자가 아닌 경우
     */
    @Transactional
    public void deletePostImage(String key, Long memberId) {
        try {
            // 이미지 엔티티 조회
            Optional<PostImage> imageOpt = postImageRepository.findByFileKey(key);
            boolean isAuthorized = false;
            
            if (imageOpt.isPresent()) {
                // DB에 이미지 정보가 있는 경우 - 소유권 검증
                PostImage image = imageOpt.get();
                
                // 소유권 검증 - DB 기반으로 소유자 검증
                if (!image.getPost().getAuthor().getId().equals(memberId)) {
                    throw new AccessDeniedException("본인 게시글의 이미지만 삭제할 수 있습니다");
                }
                
                isAuthorized = true;
            } else {
                // DB에 이미지 정보가 없는 경우 - 사용자가 해당 키로 파일 업로드했는지 확인
                String userDir = String.format("%s/%d", FilePolicy.POST_IMAGES_PATH, memberId);
                if (!key.startsWith(userDir)) {
                    // 사용자의 디렉토리가 아니면 접근 거부
                    throw new AccessDeniedException("본인이 업로드한 이미지만 삭제할 수 있습니다");
                }
                
                log.warn("DB에 이미지 정보가 없지만 키 경로 확인으로 삭제 진행: 회원={}, 키={}", memberId, key);
                isAuthorized = true;
            }
            
            // 권한 확인 완료 후 스토리지에서 파일 삭제 시도
            if (isAuthorized) {
                try {
                    // 파일 존재 여부 확인
                    if (storage.exists(key)) {
                        storage.delete(key);
                        log.debug("스토리지에서 이미지 파일 삭제 성공: 회원={}, 키={}", memberId, key);
                    } else {
                        log.warn("스토리지에 파일이 존재하지 않음: 키={}", key);
                    }
                } catch (StorageException e) {
                    // 파일 경로 검증 실패는 로그만 남기고 계속 진행
                    log.warn("파일 삭제 중 스토리지 오류 발생: {}", e.getMessage());
                }
            }
            
            // 스토리지 삭제 후 DB에서 이미지 엔티티 삭제
            if (isAuthorized && imageOpt.isPresent()) {
                postImageRepository.delete(imageOpt.get());
                log.debug("DB에서 이미지 정보 삭제 성공: 회원={}, 키={}", memberId, key);
            }
            
        } catch (AccessDeniedException e) {
            // 접근 권한 없음 - 그대로 전파
            log.error("이미지 삭제 권한 없음: 회원={}, 키={}", memberId, key);
            throw e;
        } catch (Exception e) {
            // 기타 예외
            log.error("이미지 삭제 실패: 회원={}, 키={}, 오류={}", 
                    memberId, key, e.getMessage(), e);
            throw new RuntimeException("이미지 삭제 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 파일 소유권 검증
     * 파일의 소유권을 DB 기반으로 검증합니다.
     * 
     * @param key 파일 키
     * @param memberId 요청자 ID
     * @throws AccessDeniedException 파일 소유자가 아닌 경우
     * @throws EntityNotFoundException 이미지가 존재하지 않는 경우
     */
    public void validateFileOwnership(String key, Long memberId) {
        // 게시글 이미지 소유권 확인
        Optional<PostImage> imageOpt = postImageRepository.findByFileKey(key);
        if (imageOpt.isPresent()) {
            PostImage image = imageOpt.get();
            if (!image.getPost().getAuthor().getId().equals(memberId)) {
                throw new AccessDeniedException(FilePolicy.ERR_UNAUTHORIZED_ACCESS);
            }
            return;
        }
        
        // 이미지를 찾을 수 없는 경우
        throw new EntityNotFoundException("이미지", key);
    }
    
    /**
     * 이미지 파일 유효성 검사
     * 파일 크기와 타입을 검증합니다.
     */
    private void validateImageFile(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException(FilePolicy.ERR_FILE_EMPTY);
        }
        
        if (FilePolicy.isFileSizeExceeded(file.getSize())) {
            throw new IllegalArgumentException(
                String.format(FilePolicy.ERR_FILE_TOO_LARGE, FilePolicy.MAX_FILE_SIZE_BYTES));
        }
        
        // 파일 타입 검증
        if (!FilePolicy.isAllowed(file, FilePolicy.ALLOWED_IMAGE_TYPES)) {
            throw new IllegalArgumentException(
                String.format(FilePolicy.ERR_INVALID_FILE_TYPE, String.join(", ", FilePolicy.ALLOWED_IMAGE_TYPES)));
        }
    }
}
