package com.example.community.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 파일 타입 검증 유틸리티
 * MIME 타입과 파일 시그니처(매직 넘버)를 모두 검사하여 안전성 강화
 */
public final class FileTypeValidator {
    private FileTypeValidator() {
        throw new IllegalStateException("유틸리티 클래스는 인스턴스화할 수 없습니다");
    }

    /**
     * 파일이 허용된 타입인지 검증
     * @param file 검증할 파일
     * @param allowedMime 허용된 MIME 타입 목록
     * @return 허용 여부
     */
    public static boolean isAllowed(MultipartFile file, Set<String> allowedMime) throws IOException {
        if (file == null || file.isEmpty()) return false;
        
        // 1. MIME 타입 검증
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!allowedMime.contains(ct)) return false;

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
            if (r < 0) return new byte[0];
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
        return startsWith(b, new byte[]{(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A});
    }
    
    /**
     * JPEG 파일 시그니처 검증
     */
    private static boolean isJpeg(byte[] b) {
        return startsWith(b, new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF});
    }
    
    /**
     * WebP 파일 시그니처 검증
     */
    private static boolean isWebp(byte[] b) {
        // RIFF....WEBP header
        return b.length >= 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
                && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P';
    }
    
    /**
     * GIF 파일 시그니처 검증
     */
    private static boolean isGif(byte[] b) {
        return (startsWith(b, new byte[]{'G', 'I', 'F', '8', '7', 'a'}) || 
                startsWith(b, new byte[]{'G', 'I', 'F', '8', '9', 'a'}));
    }

    /**
     * 바이트 배열이 특정 프리픽스로 시작하는지 확인
     */
    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }
}
