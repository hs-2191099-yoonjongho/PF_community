package com.example.community.security;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;


 // JWT 토큰 기반의 인증을 처리하는 필터
 
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
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

        // 이미 인증된 경우(세션 등) 덮어쓰지 않음
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        // Bearer 토큰 형식 확인 (Bearer + 공백 + 실제토큰)
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            try {
                Claims claims = jwtUtil.parseClaims(token);
                // id 기반 인증
                String idStr = claims.getSubject();
                Long id = Long.parseLong(idStr);
                Number verNum = claims.get("ver", Number.class);
                int tokenVersion = verNum != null ? verNum.intValue() : -1;
                Integer currentVersion = memberRepository.findTokenVersionById(id);
                if (tokenVersion < 0 || currentVersion == null || !currentVersion.equals(tokenVersion)) {
                    log.warn("토큰 버전 불일치 또는 미존재 (토큰: {}, DB: {})", tokenVersion, currentVersion);
                    throw new JwtException("토큰이 무효화되었습니다");
                }
                UserDetails userDetails = userDetailsService.loadUserById(id);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                log.debug("JWT 인증 처리 중 오류: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        // 다음 필터로 요청 전달
        chain.doFilter(req, res);
    }
}