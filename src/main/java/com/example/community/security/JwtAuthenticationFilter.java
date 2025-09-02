package com.example.community.security;

import com.example.community.config.JwtUtil;
import com.example.community.repository.MemberRepository;
import com.example.community.service.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


 // JWT 토큰 기반의 인증을 처리하는 필터
 
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService uds;
    private final MemberRepository memberRepository;

    /**
     * 모든 HTTP 요청에 대해 JWT 인증을 처리합니다.
     * 
     * @param req  HTTP 요청 객체
     * @param res  HTTP 응답 객체
     * @param chain 필터 체인
     * @throws ServletException 서블릿 처리 중 예외 발생 시
     * @throws IOException IO 작업 중 예외 발생 시
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");
        // Bearer 토큰 형식 확인 (Bearer + 공백 + 실제토큰)
        if (auth != null && auth.startsWith("Bearer ")) {
            // "Bearer " 접두사 제거 후 토큰 추출
            String token = auth.substring(7);
            try {
                // 토큰을 한 번만 파싱하여 Claims 객체 획득
                Claims claims = jwtUtil.parseClaims(token);
                
                // 토큰에서 사용자 이메일 추출
                String email = claims.getSubject();
                
                // 토큰에 있는 버전 가져오기 - 타입 안전성 확보
                Number verNum = claims.get("ver", Number.class);
                int tokenVersion = verNum != null ? verNum.intValue() : -1;
                
                // DB에 저장된 현재 사용자의 토큰 버전 가져오기
                int currentVersion = memberRepository.findTokenVersionByEmail(email);
                
                // 토큰 버전 검증
                if (tokenVersion == -1 || tokenVersion != currentVersion) {
                    log.warn("토큰 버전 불일치 (토큰: {}, DB: {})", tokenVersion, currentVersion);
                    throw new JwtException("토큰이 무효화되었습니다");
                }
                
                // DB조회로 MemberDetails 로딩
                var principal = (MemberDetails) uds.loadUserByUsername(email);

                // 인증 객체 생성 및 SecurityContext에 설정
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // JWT 처리 중 예외 발생 시 로그만 남기고 인증 없이 진행
                // 예외를 다시 던지면 필터 체인이 중단되므로 조용히 처리
                log.debug("JWT 인증 처리 중 오류: " + e.getMessage());
            }
        }
        // 다음 필터로 요청 전달
        chain.doFilter(req, res);
    }
}