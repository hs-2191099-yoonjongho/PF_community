package com.example.community.common;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 파일 처리 관련 정책 상수 및 유틸리티
 * 시스템 전체에서 일관된 파일 처리 규칙을 적용하기 위한 중앙 정책 클래스
 * - 파일 크기 제한
 * - 허용되는 파일 형식
 * - 저장 경로 관리
 * - 오류 메시지 표준화
 */
public final class FilePolicy {
    // 생성자 private화로 인스턴스 생성 방지
    private FilePolicy() {
        throw new IllegalStateException("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    // 파일 크기 제한
    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    public static final long MAX_TOTAL_SIZE_BYTES = 20 * 1024 * 1024; // 20MB

    // 허용되는 이미지 타입 (MIME 타입)
    public static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", 
            "image/png", 
            "image/gif", 
            "image/webp"
    );
    
    // 허용되는 문서 타입 (MIME 타입) - PDF 지원 제외
    public static final Set<String> ALLOWED_DOC_TYPES = Set.of();
    
    // 모든 업로드 허용 MIME 타입
    public static final Set<String> ALLOWED_FILE_TYPES = Set.of(
            "image/jpeg", 
            "image/png", 
            "image/gif", 
            "image/webp"
    );
    
    // 파일 타입별 매직 넘버 (파일 시그니처)
    public static final Map<String, List<byte[]>> FILE_SIGNATURES = Map.of(
        "image/jpeg", List.of(
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 },
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1 },
            new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE8 }
        ),
        "image/png", List.of(
            new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }
        ),
        "image/gif", List.of(
            new byte[] { 0x47, 0x49, 0x46, 0x38, 0x37, 0x61 },
            new byte[] { 0x47, 0x49, 0x46, 0x38, 0x39, 0x61 }
        ),
        "image/webp", List.of(
            new byte[] { 0x52, 0x49, 0x46, 0x46 } // WEBP의 경우 더 복잡한 검증이 필요할 수 있음
        )
    );

    // 디렉토리 화이트리스트
    public static final Set<String> DIR_WHITELIST = Set.of(
        "posts", "profiles"
    );

    // 오류 메시지
    public static final String ERR_FILE_EMPTY = "파일이 비어 있습니다.";
    public static final String ERR_FILE_TOO_LARGE = "파일이 너무 큽니다. 최대 %d 바이트까지 허용됩니다.";
    public static final String ERR_INVALID_FILE_TYPE = "허용되지 않는 파일 형식입니다. 허용되는 형식: %s";
    public static final String ERR_FILE_CONTENT_MISMATCH = "파일 내용이 확장자와 일치하지 않습니다.";
    public static final String ERR_TOTAL_SIZE_EXCEEDED = "전체 업로드 크기가 제한을 초과했습니다. 최대 %d 바이트까지 허용됩니다.";
    public static final String ERR_FILE_READ_ERROR = "파일을 읽는 중 오류가 발생했습니다.";
    public static final String ERR_UNAUTHORIZED_ACCESS = "파일에 대한 접근 권한이 없습니다.";
    public static final String ERR_INVALID_DIR = "허용되지 않는 디렉터리입니다.";
    public static final String ERR_PATH_TRAVERSAL = "경로에 잘못된 문자가 포함되어 있습니다.";
    
    // 파일 저장 경로 상수
    public static final String POST_IMAGES_PATH = "posts";
    public static final String PROFILE_IMAGES_PATH = "profiles";
    
    /**
     * 파일 경로 주입 공격 방지 검증
     * 다음과 같은 위험 요소들을 검사합니다:
     * - 상위 디렉토리 접근 (..)
     * - 절대 경로 시작 (/ 또는 \로 시작)
     * - 윈도우 드라이브 문자 (C: 등)
     * - URL 프로토콜 (file:, http: 등)
     * - 백슬래시 사용 (윈도우 경로)
     * 
     * @param path 검증할 경로
     * @return 안전한 경로 여부
     */
    public static boolean isPathSafe(String path) {
        if (path == null) {
            return false;
        }
        
        // 상위 디렉토리 접근 방지
        if (path.contains("..")) {
            return false;
        }
        
        // 절대 경로 시작 방지 (/ 또는 \로 시작)
        if (path.startsWith("/") || path.startsWith("\\")) {
            return false;
        }
        
        // 윈도우 드라이브 문자 방지 (예: C:)
        if (path.matches("^[A-Za-z]:.*")) {
            return false;
        }
        
        // URL 프로토콜 방지 (예: file:, http:)
        if (path.contains(":")) {
            return false;
        }
        
        // 백슬래시 방지 (윈도우 경로)
        if (path.contains("\\")) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 파일 확장자 추출
     * @param filename 파일명
     * @return 확장자 (점 제외, 소문자)
     */
    public static String getExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }
    
    /**
     * 허용된 이미지 타입인지 확인
     * @param contentType MIME 타입
     * @return 허용 여부
     */
    public static boolean isAllowedImageType(String contentType) {
        return ALLOWED_IMAGE_TYPES.contains(contentType);
    }
    
    /**
     * 허용된 문서 타입인지 확인 - PDF 지원 제외로 항상 false 반환
     * @param contentType MIME 타입
     * @return 허용 여부
     */
    public static boolean isAllowedDocType(String contentType) {
        return false; // PDF 지원 제외
    }
    
    /**
     * 파일 크기 제한 초과 여부 확인
     * @param size 파일 크기 (바이트)
     * @return 초과 여부
     */
    public static boolean isFileSizeExceeded(long size) {
        return size > MAX_FILE_SIZE_BYTES;
    }
    
    /**
     * 전체 업로드 크기 제한 초과 여부 확인
     * @param totalSize 전체 파일 크기 (바이트)
     * @return 초과 여부
     */
    public static boolean isTotalSizeExceeded(long totalSize) {
        return totalSize > MAX_TOTAL_SIZE_BYTES;
    }
}
