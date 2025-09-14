package com.example.community.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.public-base-url=http://localhost:8080/files",
        "app.storage.local.base-path=${java.io.tmpdir}/community-test-uploads"
})
class FileControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("정적 /files/** 경로는 401/403 없이 공개 접근 가능 (존재하면 200, 없으면 404)")
    void files_route_is_public() throws Exception {
        // 존재하지 않는 파일은 404여야 하고, 보안 차단(401/403)은 없어야 함
        mvc.perform(get("/files/does-not-exist.png"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s == 401 || s == 403)
                        throw new AssertionError("/files/** 가 보안에 차단됨");
                });
    }
}
