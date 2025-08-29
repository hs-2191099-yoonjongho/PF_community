package com.example.community.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public final class FileTypeValidator {
    private FileTypeValidator() {}

    // Validate by MIME and signature (magic number). Supports png, jpeg, webp, pdf.
    public static boolean isAllowed(MultipartFile file, Set<String> allowedMime) throws IOException {
        if (file == null || file.isEmpty()) return false;
        String ct = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!allowedMime.contains(ct)) return false;

        byte[] head = readHead(file, 32);
        return switch (ct) {
            case "image/png" -> isPng(head);
            case "image/jpeg" -> isJpeg(head);
            case "image/webp" -> isWebp(head);
            case "application/pdf" -> isPdf(head);
            default -> false;
        };
    }

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

    private static boolean isPng(byte[] b) {
        return startsWith(b, new byte[]{(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A});
    }
    private static boolean isJpeg(byte[] b) {
        return startsWith(b, new byte[]{(byte)0xFF,(byte)0xD8,(byte)0xFF});
    }
    private static boolean isWebp(byte[] b) {
        // RIFF....WEBP header
        return b.length >= 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F'
                && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P';
    }
    private static boolean isPdf(byte[] b) {
        return startsWith(b, new byte[]{'%', 'P','D','F','-'});
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }
}
