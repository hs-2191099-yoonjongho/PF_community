package com.example.community.security;

import com.example.community.config.JwtUtil;
import com.example.community.repository.MemberRepository;
import com.example.community.service.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    @Mock
    JwtUtil jwtUtil;
    @Mock
    CustomUserDetailsService uds;
    @Mock
    MemberRepository memberRepository;
    @Mock
    HttpServletRequest req;
    @Mock
    HttpServletResponse res;
    @Mock
    FilterChain chain;

    @InjectMocks
    JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        filter = new JwtAuthenticationFilter(jwtUtil, uds, memberRepository);
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("이미 인증된 경우 JWT 인증을 건너뛴다")
    void skipIfAlreadyAuthenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, null));
        filter.doFilterInternal(req, res, chain);
        verify(chain).doFilter(req, res);
        // jwtUtil, uds, memberRepository는 호출되지 않음
        verifyNoInteractions(jwtUtil, uds, memberRepository);
    }

    @Test
    @DisplayName("정상 JWT 인증 시 SecurityContext에 인증 정보가 설정된다")
    void authenticateWithValidJwt() throws Exception {
        when(req.getHeader("Authorization")).thenReturn("Bearer valid.jwt.token");
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseClaims("valid.jwt.token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("ver", Number.class)).thenReturn(0);
        when(memberRepository.findTokenVersionById(1L)).thenReturn(0);
        MemberDetails principal = mock(MemberDetails.class);
        when(uds.loadUserById(1L)).thenReturn(principal);
        when(principal.getAuthorities()).thenReturn(java.util.Collections.emptyList());

        filter.doFilterInternal(req, res, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(principal);
        assertThat(auth.getDetails()).isInstanceOf(WebAuthenticationDetails.class);
        verify(chain).doFilter(req, res);
    }

    @Test
    @DisplayName("토큰 버전 불일치/미존재/null이면 인증 실패 및 컨텍스트 클리어")
    void tokenVersionMismatchOrNull() throws Exception {
        when(req.getHeader("Authorization")).thenReturn("Bearer bad.jwt.token");
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseClaims("bad.jwt.token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("ver", Number.class)).thenReturn(1);
        // DB 버전 null
        when(memberRepository.findTokenVersionById(1L)).thenReturn(null);
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        // DB 버전 불일치
        when(memberRepository.findTokenVersionById(1L)).thenReturn(2);
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain, times(2)).doFilter(req, res);
    }

    @Test
    @DisplayName("JWT 파싱/인증 중 예외 발생 시 컨텍스트가 클리어된다")
    void exceptionClearsContext() throws Exception {
        when(req.getHeader("Authorization")).thenReturn("Bearer invalid.jwt.token");
        when(jwtUtil.parseClaims(anyString())).thenThrow(new JwtException("invalid"));
        filter.doFilterInternal(req, res, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }

    // 헤더 없음: 아무 것도 하지 않음
    @Test
    @DisplayName("Authorization 헤더 없음: 아무 것도 설정하지 않고 체인만 진행")
    void noAuthorizationHeader() throws Exception {
        when(req.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
        verifyNoInteractions(jwtUtil, uds, memberRepository);
    }

    // ver 클레임 없음: 토큰 무효로 처리
    @Test
    @DisplayName("ver 클레임 없음: 토큰 무효 처리 및 컨텍스트 클리어")
    void noVersionClaimTreatAsInvalid() throws Exception {
        when(req.getHeader("Authorization")).thenReturn("Bearer tkn");
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseClaims("tkn")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("ver", Number.class)).thenReturn(null); // 핵심
        // DB 값이 있어도 ver 없음이면 무효
        when(memberRepository.findTokenVersionById(1L)).thenReturn(3);

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
        verify(uds, never()).loadUserByUsername(anyString());
    }

    // UDS 단계에서 예외: 컨텍스트 클리어 보장
    @Test
    @DisplayName("UserDetails 로딩 중 예외: 컨텍스트 클리어 후 체인 진행")
    void udsThrows_clearsContext() throws Exception {
        when(req.getHeader("Authorization")).thenReturn("Bearer tkn");
        Claims claims = mock(Claims.class);
        when(jwtUtil.parseClaims("tkn")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("1");
        when(claims.get("ver", Number.class)).thenReturn(1);
        when(memberRepository.findTokenVersionById(1L)).thenReturn(1);
        when(uds.loadUserById(1L))
                .thenThrow(new RuntimeException("UDS fail"));

        filter.doFilterInternal(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(req, res);
    }
}
