package com.example.community.util;

import com.example.community.common.FilePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

class FileTypeValidatorTest {

        @Test
        @DisplayName("PNG, JPEG, GIF, WEBP 매직넘버 검증 통과")
        void allowed_images_pass() throws IOException {
                MockMultipartFile png = new MockMultipartFile("f", "a.png", "image/png",
                                new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A });
                MockMultipartFile jpg = new MockMultipartFile("f", "a.jpg", "image/jpeg",
                                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x11 });
                MockMultipartFile gif = new MockMultipartFile("f", "a.gif", "image/gif",
                                new byte[] { 'G', 'I', 'F', '8', '9', 'a' });
                MockMultipartFile webp = new MockMultipartFile("f", "a.webp", "image/webp",
                                new byte[] { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P' });

                Set<String> allowed = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
                assertThat(FilePolicy.isAllowed(png, allowed)).isTrue();
                assertThat(FilePolicy.isAllowed(jpg, allowed)).isTrue();
                assertThat(FilePolicy.isAllowed(gif, allowed)).isTrue();
                assertThat(FilePolicy.isAllowed(webp, allowed)).isTrue();
        }

        @Test
        @DisplayName("MIME 위장 또는 매직넘버 불일치 차단")
        void disguised_or_mismatch_blocked() throws IOException {
                // MIME은 png인데 내용은 텍스트 -> 차단
                MockMultipartFile fake = new MockMultipartFile("f", "a.png", "image/png",
                                "oops".getBytes(StandardCharsets.UTF_8));
                Set<String> allowed = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
                assertThat(FilePolicy.isAllowed(fake, allowed)).isFalse();
        }
}
