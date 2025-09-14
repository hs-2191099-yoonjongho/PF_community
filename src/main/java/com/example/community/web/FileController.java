package com.example.community.web;

import com.example.community.common.FilePolicy;
import com.example.community.security.MemberDetails;
import com.example.community.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 파일 업로드 및 관리를 위한 컨트롤러
 * 게시글 이미지 파일 업로드/삭제 기능을 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 게시글용 이미지 업로드 API - 다중 파일 업로드 지원
     * 한 번에 여러 이미지를 업로드할 수 있습니다.
     * 로그인한 사용자만 이용 가능합니다.
     * 
     * @return 업로드된 이미지의 키 목록 (클라이언트는 이 키를 사용하여 게시글 생성/수정)
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/posts/images", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadPostImages(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal MemberDetails me) {
        // NPE 방지: 비어 있음 체크 후 로그
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(FilePolicy.ERR_FILE_EMPTY);
        }
        log.info("게시글 이미지 업로드 요청: 파일 수={}, 사용자ID={}", files.size(), me.getId());

        // 파일 개수 상한 제한 (최대 10개)
        final int MAX_FILES_COUNT = 10;
        if (files.size() > MAX_FILES_COUNT) {
            throw new IllegalArgumentException("한 번에 최대 " + MAX_FILES_COUNT + "개의 파일만 업로드할 수 있습니다");
        }

        // 각 파일의 타입/크기/MIME 1차 필터
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("빈 파일은 업로드할 수 없습니다");
            }
            if (file.getSize() > FilePolicy.MAX_FILE_SIZE_BYTES) {
                throw new IllegalArgumentException(
                        String.format(FilePolicy.ERR_FILE_TOO_LARGE, FilePolicy.MAX_FILE_SIZE_BYTES));
            }
            // MIME 1차 필터: 화이트리스트 기반
            String ct = (file.getContentType() == null ? "" : file.getContentType().toLowerCase());
            if (!FilePolicy.ALLOWED_IMAGE_TYPES.contains(ct)) {
                throw new IllegalArgumentException(
                        String.format(FilePolicy.ERR_INVALID_FILE_TYPE, FilePolicy.ALLOWED_IMAGE_TYPES));
            }
        }

        // 전체 크기 제한 검증
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (FilePolicy.isTotalSizeExceeded(totalSize)) {
            throw new IllegalArgumentException(
                    String.format(FilePolicy.ERR_TOTAL_SIZE_EXCEEDED, FilePolicy.MAX_TOTAL_SIZE_BYTES));
        }

        List<String> uploaded = fileService.uploadPostImageKeys(files, me.getId());
        log.info("게시글 이미지 업로드 완료: 업로드 수={}", uploaded.size());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "fileKeys", uploaded));
    }

    /**
     * 게시글 이미지 삭제 API
     * 이미지 키를 기준으로 이미지를 삭제합니다.
     * 로그인한 사용자만 이용 가능하며, 본인 게시글의 이미지만 삭제 가능합니다.
     * 
     * 주의: key는 슬래시를 포함할 수 있어 쿼리 파라미터 방식 사용
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/posts/images")
    public ResponseEntity<?> deletePostImage(
            @RequestParam("key") String key,
            @AuthenticationPrincipal MemberDetails me) {
        log.info("게시글 이미지 삭제 요청: 키={}, 사용자ID={}", key, me.getId());

        // 키가 비어있는지 확인
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("삭제할 이미지 키가 없습니다");
        }

        // 1. Path Traversal 방지 (보안: 악의적인 경로 접근 차단)
        if (!FilePolicy.isPathSafe(key)) {
            throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
        }

        // 2. 권한 검증 및 삭제 처리 - DB 연동 (보안: 권한 없는 파일 삭제 방지)
        fileService.deletePostImage(key, me.getId());
        log.info("게시글 이미지 삭제 완료: 키={}", key);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "deleted", true,
                "message", "이미지가 성공적으로 삭제되었습니다"));
    }
}
