package com.example.community.service;

import com.example.community.common.FilePolicy;
import com.example.community.repository.PostImageRepository;
import com.example.community.service.dto.ImageMeta;
import com.example.community.service.exception.ForbiddenOperationException;
import com.example.community.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


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
     * 
     * @param files    업로드할 이미지 파일 목록
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
     * 
     * @param files    업로드할 이미지 파일 목록
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
     * 
     * @param files    업로드할 파일 목록
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
     * @param key      삭제할 이미지 키
     * @param memberId 삭제 요청자 ID
     * @throws ForbiddenOperationException 이미지 소유자가 아닌 경우
     */
    @Transactional
    public void deletePostImage(String key, Long memberId) {
        requireSafeKey(key);

        // 1) DB 기반 권한 + 삭제를 '한 쿼리'로 (TOCTOU 방지)
        int deleted = postImageRepository.deleteByFileKeyAndPost_Author_Id(key, memberId);
        if (deleted == 0) {
            // DB에 없을 때만, 본인 디렉토리 키인지 확인 후(임시 업로드 등) 허용
            ensureOwnedPathOrThrow(key, memberId);
        }

        // 2) 커밋 이후 스토리지 삭제
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            storage.delete(key);
                        } catch (Exception e) {
                            log.error("Storage delete failed: key={}", key, e);
                        }
                    }
                });
    }



    // 삭제 진입 시 경로 검증
    private void requireSafeKey(String key) {
        if (!FilePolicy.isPathSafe(key))
            throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
        if (!key.startsWith(FilePolicy.POST_IMAGES_PATH + "/"))
            throw new IllegalArgumentException("허용되지 않은 파일 경로");
    }

    // DB에 없을 때 본인 디렉토리 키인지 확인
    private void ensureOwnedPathOrThrow(String key, Long memberId) {
        String userDir = FilePolicy.POST_IMAGES_PATH + "/" + memberId + "/";
        if (!key.startsWith(userDir)) {
            throw new ForbiddenOperationException("본인이 업로드한 이미지만 삭제할 수 있습니다");
        }
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
