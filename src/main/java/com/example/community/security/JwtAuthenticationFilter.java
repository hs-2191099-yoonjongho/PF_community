package com.example.community.security;

import com.example.community.config.JwtUtil;
import com.example.community.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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
            if (jwtUtil.validateAccess(token)) {
                String username = jwtUtil.getUsername(token);
                // A안) DB조회로 MemberDetails 로딩
                var principal = (MemberDetails) uds.loadUserByUsername(username);

                // B안) 토큰에 uid/roles 넣었으면 DB조회 없이 직접 MemberDetails 생성 가능
                // Long uid = jwt.getUid(token); List<String> roles = jwt.getRoles(token) ...

                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(req, res);
    }
}