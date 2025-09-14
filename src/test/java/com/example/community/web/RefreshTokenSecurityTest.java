
package com.example.community.web;

import static org.mockito.ArgumentMatchers.anyLong;

import com.example.community.config.JwtUtil;
import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.MemberRepository;
import com.example.community.service.MemberService;
import com.example.community.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "ALLOWED_ORIGINS=http://allowed.example,https://another.example",
        "refresh.exp-ms=3600000",
        "refresh.cookie.name=refresh_token",
        "refresh.cookie.path=/api/auth",
        "refresh.cookie.secure=false",
        "refresh.cookie.same-site=Lax"
})
@Import(RefreshTokenSecurityTest.TestConfig.class)
public class RefreshTokenSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("허용된 Origin에서 보낸 리프레시 요청은 성공해야 함")
    void refreshToken_withAllowedOrigin_shouldSucceed() throws Exception {
        // Given
        Member testMember = createTestMember();
        RefreshToken refreshToken = createRefreshToken(testMember);

        when(refreshTokenService.validateAndGet(anyString())).thenReturn(refreshToken);
        when(refreshTokenService.rotate(anyString())).thenReturn("new-refresh-token");
        when(jwtUtil.generateAccessToken(anyLong())).thenReturn("new.access.token");

        // When & Then
        performRefreshRequest("http://allowed.example", "XMLHttpRequest")
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용되지 않은 Origin에서 보낸 리프레시 요청은 실패해야 함")
    void refreshToken_withDisallowedOrigin_shouldFail() throws Exception {
        // When & Then
        performRefreshRequest("http://malicious.example", "XMLHttpRequest")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Origin 헤더가 없는 리프레시 요청은 실패해야 함")
    void refreshToken_withoutOrigin_shouldFail() throws Exception {
        // When & Then - Origin 헤더 없이 요청
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header("X-Requested-With", "XMLHttpRequest")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("X-Requested-With 헤더가 없는 리프레시 요청은 실패해야 함")
    void refreshToken_withoutXRequestedWith_shouldFail() throws Exception {
        // When & Then - X-Requested-With 헤더 없이 요청
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "http://allowed.example")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("허용된 Origin에서 보낸 로그아웃 요청은 성공해야 함")
    void logout_withAllowedOrigin_shouldSucceed() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "http://allowed.example")
                .header("X-Requested-With", "XMLHttpRequest") // AJAX 요청 헤더 추가
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "token-to-revoke")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("허용되지 않은 Origin에서 보낸 로그아웃 요청은 실패해야 함")
    void logout_withDisallowedOrigin_shouldFail() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, "http://malicious.example")
                .header("X-Requested-With", "XMLHttpRequest") // AJAX 요청 헤더 추가
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "token-to-revoke")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Origin 헤더가 없는 로그아웃 요청은 실패해야 함")
    void logout_withoutOrigin_shouldFail() throws Exception {
        // When & Then - Origin 헤더 없이 요청
        mockMvc.perform(post("/api/auth/logout")
                .with(csrf())
                .header("X-Requested-With", "XMLHttpRequest") // AJAX 요청 헤더 추가
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "token-to-revoke")))
                .andExpect(status().isForbidden());
    }

    // Helper 메서드
    private ResultActions performRefreshRequest(String origin, String xRequestedWith) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh")
                .with(csrf())
                .header(HttpHeaders.ORIGIN, origin)
                .header("X-Requested-With", xRequestedWith)
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token")));
    }

    private Member createTestMember() {
        return Member.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .roles(java.util.Set.of("ROLE_USER"))
                .build();
    }

    private RefreshToken createRefreshToken(Member member) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash("hashedToken")
                .user(member)
                .revoked(false)
                .expiresAt(java.time.Instant.now().plusSeconds(3600))
                .build();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        AuthenticationManager authenticationManager() {
            return Mockito.mock(AuthenticationManager.class);
        }

        @Bean
        MemberRepository memberRepository() {
            return Mockito.mock(MemberRepository.class);
        }

        @Bean
        MemberService memberService() {
            return Mockito.mock(MemberService.class);
        }
    }
}
