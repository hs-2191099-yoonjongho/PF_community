package com.example.community.security;

import com.example.community.config.JwtUtil;
import com.example.community.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService uds;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                if (jwtUtil.validateAccess(token)) {
                    String email = jwtUtil.getEmail(token);
                    // A안) DB조회로 MemberDetails 로딩
                    var principal = (MemberDetails) uds.loadUserByUsername(email);

                    // B안) 토큰에 uid/roles 넣었으면 DB조회 없이 직접 MemberDetails 생성 가능
                    // Long uid = jwt.getUid(token); List<String> roles = jwt.getRoles(token) ...

                    var authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                // JWT 처리 중 예외 발생 시 로그만 남기고 인증 없이 진행
                // 예외를 다시 던지면 필터 체인이 중단되므로 조용히 처리
                log.debug("JWT 인증 처리 중 오류: " + e.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}