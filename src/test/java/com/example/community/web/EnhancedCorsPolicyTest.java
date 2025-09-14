package com.example.community.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 정책 강화 테스트 - Origin 헤더 검증 추가 버전
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=http://test1.example,http://test2.example",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public class EnhancedCorsPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("허용된 Origin에서의 프리플라이트 요청은 성공해야 함")
    void preflightRequest_fromAllowedOrigin_shouldSucceed() throws Exception {
        // Given & When
        ResultActions result = performPreflightRequest("http://test1.example");

        // Then
        result.andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://test1.example"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    // Helper 메서드
    private ResultActions performPreflightRequest(String origin) throws Exception {
        return mockMvc.perform(options("/api/auth/refresh")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Requested-With, Content-Type")
                .with(csrf()));
    }
}
