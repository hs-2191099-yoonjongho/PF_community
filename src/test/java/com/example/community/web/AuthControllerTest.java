package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.MemberRepository;
import com.example.community.security.MemberDetails;
import com.example.community.service.MemberService;
import com.example.community.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 인증 컨트롤러 테스트
 * 로그인, 토큰 갱신, 로그아웃 기능 테스트
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
                "refresh.exp-ms=3600000",
                "refresh.cookie.name=refresh_token",
                "refresh.cookie.path=/api/auth",
                "refresh.cookie.secure=false",
                "refresh.cookie.same-site=Lax",
                "ALLOWED_ORIGINS=http://localhost:3000"
})
class AuthControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private AuthenticationManager authenticationManager;

        @MockitoBean
        private JwtUtil jwtUtil;

        @MockitoBean
        private MemberRepository memberRepository;

        @MockitoBean
        private RefreshTokenService refreshTokenService;

        @MockitoBean
        private MemberService memberService;

        @Test
        @DisplayName("로그인 성공 시 액세스 토큰과 리프레시 토큰 쿠키 반환")
        void loginSuccessTest() throws Exception {
                // given
                String loginRequestJson = "{\"email\":\"test@example.com\",\"password\":\"password\"}";

                Member testMember = Member.builder()
                                .id(1L)
                                .username("testuser")
                                .email("test@example.com")
                                .password("password")
                                .roles(Set.of("ROLE_USER"))
                                .build();
                // memberService.findByEmail 삭제됨: memberRepository.findByEmail로 대체
                when(memberRepository.findByEmail(anyString())).thenReturn(Optional.of(testMember));

                MemberDetails memberDetails = new MemberDetails(
                                testMember.getId(),
                                testMember.getEmail(),
                                testMember.getPassword(),
                                Set.of(new SimpleGrantedAuthority("ROLE_USER")));

                Authentication authentication = new UsernamePasswordAuthenticationToken(
                                memberDetails, null, memberDetails.getAuthorities());

                when(authenticationManager.authenticate(any(Authentication.class))).thenReturn(authentication);
                when(memberRepository.findByEmail(anyString())).thenReturn(Optional.of(testMember));
                when(jwtUtil.generateAccessToken(anyLong())).thenReturn("test.access.token"); // id 기반이지만, 실제 컨트롤러는 id를
                                                                                              // long으로 넘김
                when(refreshTokenService.issueByUserId(anyLong())).thenReturn("test-refresh-token");

                // when & then
                mockMvc.perform(post("/api/auth/login")
                                .with(csrf())
                                .header("Origin", "http://localhost:3000")
                                .header("X-Requested-With", "XMLHttpRequest") // AJAX 요청 헤더 추가
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginRequestJson))
                                .andExpect(status().isOk())
                                .andExpect(cookie().exists("refresh_token"))
                                .andExpect(cookie().httpOnly("refresh_token", true))
                                .andExpect(header().exists("Cache-Control"))
                                .andExpect(jsonPath("$.accessToken").value("test.access.token"));
        }

        @Test
        @DisplayName("리프레시 토큰으로 새 액세스 토큰 발급")
        void refreshTokenTest() throws Exception {
                // given
                Member testMember = Member.builder()
                                .id(1L)
                                .username("testuser")
                                .email("test@example.com")
                                .password("password")
                                .roles(Set.of("ROLE_USER"))
                                .build();

                RefreshToken refreshToken = RefreshToken.builder()
                                .id(1L)
                                .tokenHash("hashedToken")
                                .user(testMember)
                                .revoked(false)
                                .build();

                when(refreshTokenService.validateAndGet(anyString())).thenReturn(refreshToken);
                when(refreshTokenService.rotate(anyString())).thenReturn("new-refresh-token");
                when(jwtUtil.generateAccessToken(anyLong())).thenReturn("new.access.token"); // id 기반

                // when & then
                mockMvc.perform(post("/api/auth/refresh")
                                .with(csrf())
                                .header("Origin", "http://localhost:3000")
                                .header("X-Requested-With", "XMLHttpRequest")
                                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "old-refresh-token")))
                                .andExpect(status().isOk())
                                .andExpect(cookie().exists("refresh_token"))
                                .andExpect(cookie().value("refresh_token", "new-refresh-token"))
                                .andExpect(header().exists("Cache-Control"))
                                .andExpect(jsonPath("$.accessToken").value("new.access.token"));
        }

        @Test
        @DisplayName("로그아웃 시 리프레시 토큰 폐기 및 쿠키 삭제")
        void logoutTest() throws Exception {
                // given - 특별한 설정 필요 없음

                // when & then
                mockMvc.perform(post("/api/auth/logout")
                                .with(csrf())
                                .header("Origin", "http://localhost:3000")
                                .header("X-Requested-With", "XMLHttpRequest") // AJAX 요청 헤더 추가
                                .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token-to-revoke")))
                                .andExpect(status().isOk())
                                .andExpect(cookie().exists("refresh_token"))
                                .andExpect(cookie().maxAge("refresh_token", 0))
                                .andExpect(header().exists("Cache-Control"));
        }
}
