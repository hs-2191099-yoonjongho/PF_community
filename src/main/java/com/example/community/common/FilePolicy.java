package com.example.community.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 파일 처리 관련 정책 상수 및 유틸리티
 * - 파일 크기 제한
 * - 허용되는 파일 형식
 * - 저장 경로 관리
 * - 오류 메시지 표준화
 * - 파일 타입 검증 (매직 넘버 검사)
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
            "image/webp");

    // 오류 메시지
    public static final String ERR_FILE_EMPTY = "파일이 비어 있습니다.";
    public static final String ERR_FILE_TOO_LARGE = "파일이 너무 큽니다. 최대 %d 바이트까지 허용됩니다.";
    public static final String ERR_INVALID_FILE_TYPE = "허용되지 않는 파일 형식입니다. 허용되는 형식: %s";
    public static final String ERR_TOTAL_SIZE_EXCEEDED = "전체 업로드 크기가 제한을 초과했습니다. 최대 %d 바이트까지 허용됩니다.";
    public static final String ERR_UNAUTHORIZED_ACCESS = "파일에 대한 접근 권한이 없습니다.";
    public static final String ERR_PATH_TRAVERSAL = "경로에 잘못된 문자가 포함되어 있습니다.";

    // 파일 저장 경로 상수
    public static final String POST_IMAGES_PATH = "posts";

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
     * 파일 크기 제한 초과 여부 확인
     * 
     * @param size 파일 크기 (바이트)
     * @return 초과 여부
     */
    public static boolean isFileSizeExceeded(long size) {
        return size > MAX_FILE_SIZE_BYTES;
    }

    /**
     * 전체 업로드 크기 제한 초과 여부 확인
     * 
     * @param totalSize 전체 파일 크기 (바이트)
     * @return 초과 여부
     */
    public static boolean isTotalSizeExceeded(long totalSize) {
        return totalSize > MAX_TOTAL_SIZE_BYTES;
    }

    /**
     * 파일이 허용된 타입인지 검증
     * 
     * @param file        검증할 파일
     * @param allowedMime 허용된 MIME 타입 목록
     * @return 허용 여부
     */
    public static boolean isAllowed(MultipartFile file, Set<String> allowedMime) throws IOException {
        if (file == null || file.isEmpty())
            return false;

        // 1. MIME 타입 검증
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!allowedMime.contains(ct))
            return false;

        // 2. 파일 시그니처(매직 넘버) 검증
        byte[] head = readHead(file, 32);
        return switch (ct) {
            case "image/png" -> isPng(head);
            case "image/jpeg" -> isJpeg(head);
            case "image/webp" -> isWebp(head);
            case "image/gif" -> isGif(head);
            default -> false;
        };
    }

    /**
     * 파일 헤더(시작 부분) 읽기
     */
    private static byte[] readHead(MultipartFile file, int n) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[n];
            int r = in.read(buf);
            if (r < 0)
                return new byte[0];
            if (r < n) {
                byte[] small = new byte[r];
                System.arraycopy(buf, 0, small, 0, r);
                return small;
            }
            return buf;
        }
    }

    /**
     * PNG 파일 시그니처 검증
     */
    private static boolean isPng(byte[] b) {
        return startsWith(b, new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
    }

    /**
     * JPEG 파일 시그니처 검증
     */
    private static boolean isJpeg(byte[] b) {
        return startsWith(b, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF });
    }

    /**
     * WebP 파일 시그니처 검증
     */
    private static boolean isWebp(byte[] b) {
        // RIFF....WEBP header
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /**
     * GIF 파일 시그니처 검증
     */
    private static boolean isGif(byte[] b) {
        return (startsWith(b, new byte[] { 'G', 'I', 'F', '8', '7', 'a' }) ||
                startsWith(b, new byte[] { 'G', 'I', 'F', '8', '9', 'a' }));
    }

    /**
     * 바이트 배열이 특정 프리픽스로 시작하는지 확인
     */
    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length)
            return false;
        for (int i = 0; i < prefix.length; i++)
            if (data[i] != prefix[i])
                return false;
        return true;
    }
}
