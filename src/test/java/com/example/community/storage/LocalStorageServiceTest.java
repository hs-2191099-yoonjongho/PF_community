package com.example.community.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.example.community.storage.Storage.StoredFile;

class LocalStorageServiceTest {

    @Test
    @DisplayName("경로 탈출 시도는 StorageException")
    void path_traversal_blocked() {
        LocalStorageService s = new LocalStorageService();
        // basePath와 publicBaseUrl 주입
        TestUtil.setField(s, "basePath", Path.of(System.getProperty("java.io.tmpdir"), "ls-test").toString());
        TestUtil.setField(s, "publicBaseUrl", "http://localhost:8080/files");
        s.init();

        assertThatThrownBy(() -> s.store(new MockMultipartFile("f","x.png","image/png", new byte[]{1,2,3,4,5,6,7,8}), "../../.."))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("매직넘버 불일치 파일은 차단")
    void magic_number_mismatch_blocked() {
        LocalStorageService s = new LocalStorageService();
        TestUtil.setField(s, "basePath", Path.of(System.getProperty("java.io.tmpdir"), "ls-test").toString());
        TestUtil.setField(s, "publicBaseUrl", "http://localhost:8080/files");
        s.init();

        // contentType은 png인데 실제 바이트는 PNG 시그니처가 아님 → 차단
        MockMultipartFile bad = new MockMultipartFile("f","x.png","image/png", new byte[]{0x00,0x01,0x02,0x03});
        assertThatThrownBy(() -> s.store(bad, "posts"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    @DisplayName("저장된 파일 URL 매핑은 app.public-base-url을 기준으로 생성")
    void url_mapping_uses_public_base() throws Exception {
        LocalStorageService s = new LocalStorageService();
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "ls-test");
        Files.createDirectories(base);
        TestUtil.setField(s, "basePath", base.toString());
        TestUtil.setField(s, "publicBaseUrl", "http://localhost:8080/files");
        s.init();

        byte[] png = new byte[]{(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A, 0,0,0,0};
        MockMultipartFile ok = new MockMultipartFile("f","ok.png","image/png", png);
    StoredFile stored = s.store(ok, "posts");

    assertThat(stored.url()).startsWith("http://localhost:8080/files/posts/");
    assertThat(stored.key()).startsWith("posts/");
    }
}

class TestUtil {
    static void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
