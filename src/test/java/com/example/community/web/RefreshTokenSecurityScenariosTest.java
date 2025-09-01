package com.example.community.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 및 Origin 헤더 보안 통합 테스트
 * 다양한 보안 시나리오를 테스트합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=https://trusted-site.com,https://another-trusted.com",
        "refresh.exp-ms=3600000",
        "refresh.cookie.name=refresh_token",
        "refresh.cookie.path=/api/auth",
        "refresh.cookie.secure=false",
        "refresh.cookie.same-site=Lax",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public class RefreshTokenSecurityScenariosTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("공격 시나리오 1: 허용되지 않은 사이트에서 Origin 헤더만 사용해 리프레시 토큰 재발급 시도")
    void attackScenario1_onlyOriginHeader_shouldBeDenied() throws Exception {
        // 공격자가 정상 사이트의 Origin 헤더는 알고 있지만, X-Requested-With 헤더는 누락
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "https://trusted-site.com")
                // X-Requested-With 헤더 의도적 누락
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "stolen-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공격 시나리오 2: X-Requested-With 헤더만으로 리프레시 토큰 재발급 시도")
    void attackScenario2_onlyXRequestedWithHeader_shouldBeDenied() throws Exception {
        // 공격자가 X-Requested-With 헤더는 설정했지만 Origin 헤더 누락
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                // Origin 헤더 의도적 누락
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "stolen-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("공격 시나리오 3: 가짜 Origin과 올바른 X-Requested-With 헤더로 리프레시 토큰 재발급 시도")
    void attackScenario3_fakeOriginWithCorrectXRW_shouldBeDenied() throws Exception {
        // 공격자가 가짜 Origin과 올바른 X-Requested-With 헤더 사용
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "https://evil-site.com") // 허용되지 않은 오리진
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "stolen-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("정상 시나리오: 올바른 헤더와 토큰으로 리프레시 시도")
    void legitimateScenario_correctHeadersAndToken_shouldBeAccepted() throws Exception {
        // 정상 사용자의 리프레시 요청 (실제 서비스 호출은 Mock으로 처리되므로 401 예상)
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "https://trusted-site.com")
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "valid-token")))
                .andExpect(status().isUnauthorized()); // 토큰 검증 실패지만 헤더 검증은 통과
    }

    @Test
    @DisplayName("로그아웃 시나리오: 가짜 Origin으로 로그아웃 시도")
    void logoutScenario_withFakeOrigin_shouldBeDenied() throws Exception {
        // 공격자가 가짜 Origin으로 로그아웃 시도
        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "https://evil-site.com")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "stolen-token")))
                .andExpect(status().isForbidden());
    }
}
