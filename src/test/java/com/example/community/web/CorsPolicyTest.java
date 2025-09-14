package com.example.community.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=http://allowed.example",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class CorsPolicyTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("프리플라이트 요청은 200과 CORS 헤더를 반환")
    void preflight_ok_with_headers() throws Exception {
        mvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://allowed.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Authorization, Content-Type, X-Requested-With, Accept"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://allowed.example"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
