package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.service.MemberService;
import com.example.community.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.http.HttpHeaders;
import com.example.community.repository.MemberRepository;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@TestPropertySource(properties = {
        "refresh.cookie.name=refresh_token",
        "refresh.cookie.path=/api/auth",
        "refresh.cookie.secure=false",
        "refresh.cookie.same-site=Lax",
        "refresh.exp-ms=3600000",
        "ALLOWED_ORIGINS=http://test1.example,http://test2.example"
})
@Import(AuthControllerSecurityTest.TestConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("/api/auth/refresh - 헤더 없으면 403")
    void refresh_forbidden_without_header() throws Exception {
        mvc.perform(post("/api/auth/refresh").with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("/api/auth/refresh - 헤더는 있으나 쿠키 없으면 401")
    void refresh_unauthorized_without_cookie() throws Exception {
        mvc.perform(post("/api/auth/refresh").with(csrf())
                .header("X-Requested-With", "XMLHttpRequest")
                .header(HttpHeaders.ORIGIN, "http://test1.example")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/api/auth/refresh - 헤더+쿠키 있으면 200, Set-Cookie 포함, accessToken 반환")
    void refresh_success_with_header_and_cookie() throws Exception {
        RefreshToken token = Mockito.mock(RefreshToken.class);
        Member user = Member.builder().id(1L).email("user@example.com").username("u").password("p").build();
        when(token.getUser()).thenReturn(user);
        when(refreshTokenService.validateAndGet("old")).thenReturn(token);
        when(refreshTokenService.rotate("old")).thenReturn("new");
        when(jwtUtil.generateAccessToken(anyLong())).thenReturn("access");

        mvc.perform(post("/api/auth/refresh").with(csrf())
                .header("X-Requested-With", "XMLHttpRequest")
                .header(HttpHeaders.ORIGIN, "http://test1.example")
                .cookie(new Cookie("refresh_token", "old"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token")))
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        RefreshTokenService refreshTokenService() {
            return Mockito.mock(RefreshTokenService.class);
        }

        @Bean
        JwtUtil jwtUtil() {
            return Mockito.mock(JwtUtil.class);
        }

        @Bean
        MemberService memberService() {
            return Mockito.mock(MemberService.class);
        }

        @Bean
        AuthenticationManager authenticationManager() {
            return Mockito.mock(AuthenticationManager.class);
        }

        @Bean
        MemberRepository memberRepository() {
            return Mockito.mock(MemberRepository.class);
        }
    }
}
