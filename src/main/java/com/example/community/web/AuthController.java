package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.service.MemberService;
import com.example.community.service.RefreshTokenService;
import com.example.community.service.exception.TokenReuseDetectedException;
import com.example.community.web.dto.AuthWebDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Arrays;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager am;
    private final JwtUtil jwt;
    private final RefreshTokenService refreshTokenService;
    private final MemberService memberService;

    @Value("${refresh.exp-ms}")
    private long refreshExpMs;
    @Value("${refresh.cookie.name}")
    private String cookieName; // 예: "refresh_token"
    @Value("${refresh.cookie.path}")
    private String cookiePath; // 예: "/api/auth"
    @Value("${refresh.cookie.secure}")
    private boolean cookieSecure; // dev=false, prod=true
    @Value("${refresh.cookie.same-site}")
    private String sameSite; // "Lax" | "None" | "Strict"
    @Value("${refresh.cookie.domain:}")
    private String cookieDomain; // ★ 선택(없으면 빈 문자열)
    @Value("${ALLOWED_ORIGINS:http://localhost:3000}")
    private String allowedOriginsStr;

    // 허용된 Origin 목록 - 성능 최적화를 위해 계산 결과 캐싱
    private List<String> allowedOrigins;

    // 허용된 Origin 목록 가져오기 (지연 초기화)
    private List<String> getAllowedOrigins() {
        if (allowedOrigins == null) {
            allowedOrigins = Arrays.stream(allowedOriginsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
        }
        return allowedOrigins;
    }

    /* 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody AuthWebDtos.SignupRequest req) {
        try {
            // 웹 DTO를 서비스 DTO로 변환하여 서비스 계층에 전달
            memberService.signUp(req.toServiceDto());
            return ResponseEntity.ok().body(Map.of("success", true, "message", "회원가입 성공"));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /* 로그인: Access 발급 + Refresh 쿠키 */
    @PostMapping("/login")
    public ResponseEntity<?> login(HttpServletRequest request, @Valid @RequestBody AuthWebDtos.LoginRequest req) {
        // CSRF 완화: 커스텀 헤더 강제(X-Requested-With)
        String xrw = request.getHeader("X-Requested-With");
        if (xrw == null || !"XMLHttpRequest".equalsIgnoreCase(xrw)) {
            return ResponseEntity.status(403).body(Map.of("message", "AJAX 요청이 필요합니다"));
        }

        // Origin 헤더 검증
        String origin = request.getHeader("Origin");
        List<String> allowedOrigins = getAllowedOrigins();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return ResponseEntity.status(403).body(Map.of("message", "허용되지 않은 출처의 요청입니다"));
        }

        var auth = am.authenticate(new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        var principal = (com.example.community.security.MemberDetails) auth.getPrincipal();
        long userId = principal.id();
        String access = jwt.generateAccessToken(userId);
        String refreshRaw = refreshTokenService.issueByUserId(userId);

        ResponseCookie loginCookie = createCookie(refreshRaw, refreshExpMs);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, loginCookie.toString()) // ★ 상수 사용
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // 캐시 방지
                .body(new AuthWebDtos.TokenResponse(access));
    }

    /* 리프레시: 새 Access + (회전된) 새 Refresh 쿠키 */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req) { // ★ 와일드카드 타입으로 변경
        // CSRF 완화: 커스텀 헤더 강제(X-Requested-With)
        String xrw = req.getHeader("X-Requested-With");
        if (xrw == null || !"XMLHttpRequest".equalsIgnoreCase(xrw)) {
            return ResponseEntity.status(403).body(Map.of("message", "AJAX 요청이 필요합니다"));
        }

        // Origin 헤더 검증
        String origin = req.getHeader("Origin");
        List<String> allowedOrigins = getAllowedOrigins();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return ResponseEntity.status(403).body(Map.of("message", "허용되지 않은 출처의 요청입니다"));
        }

        // 1) 쿠키에서 refresh 읽기 (설정된 이름으로)
        String refreshRaw = readCookie(req, cookieName); // ★ 이름 일관화
        if (refreshRaw == null || refreshRaw.isBlank()) {
            return unauthorizedWithCookieDelete("리프레시 토큰이 없습니다");
        }

        // 2) 서버 저장소에서 유효성 검사(만료/폐기 여부 포함)

        RefreshToken current = null;
        try {
            current = refreshTokenService.validateAndGet(refreshRaw);
        } catch (TokenReuseDetectedException e) {
            log.warn("토큰 재사용 공격 감지: ip={}, ua={}", req.getRemoteAddr(), req.getHeader("User-Agent"), e);
            return unauthorizedWithCookieDelete("보안 위협이 감지되었습니다. 다시 로그인해주세요.");
        } catch (Exception e) {
            log.debug("리프레시 토큰 검증 실패: {}", e.getMessage());
            return unauthorizedWithCookieDelete("유효하지 않은 리프레시 토큰입니다");
        }
        if (current == null) {
            return unauthorizedWithCookieDelete("유효하지 않은 리프레시 토큰입니다");
        }
        // 3) 회전(rotate): 기존 토큰 무효화 + 새 refresh 발급 (rawToken 기반)
        String newRaw = refreshTokenService.rotate(refreshRaw);
        // 4) 새 access 발급
        String access = jwt.generateAccessToken(current.getUser().getId());

        // 5) 동일 속성으로 새 refresh 쿠키 설정
        ResponseCookie refreshCookie = createCookie(newRaw, refreshExpMs);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // 캐시 방지
                .body(new AuthWebDtos.TokenResponse(access));
    }

    /* 로그아웃: Refresh 폐기 + 쿠키 삭제(동일 속성 + Max-Age=0) */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req,
            @AuthenticationPrincipal com.example.community.security.MemberDetails me) {
        // CSRF 완화: 커스텀 헤더 강제(X-Requested-With)
        String xrw = req.getHeader("X-Requested-With");
        if (xrw == null || !"XMLHttpRequest".equalsIgnoreCase(xrw)) {
            return ResponseEntity.status(403).body(Map.of("message", "AJAX 요청이 필요합니다"));
        }

        // Origin 헤더 검증
        String origin = req.getHeader("Origin");
        List<String> allowedOrigins = getAllowedOrigins();
        if (origin == null || !allowedOrigins.contains(origin)) {
            return ResponseEntity.status(403).body(Map.of("message", "허용되지 않은 출처의 요청입니다"));
        }

        // 리프레시 토큰 폐기
        String refreshRaw = readCookie(req, cookieName);
        if (refreshRaw != null && !refreshRaw.isBlank()) {
            refreshTokenService.revoke(refreshRaw);
        }

        // 인증된 사용자인 경우 토큰 버전 증가 (모든 액세스 토큰 무효화)
        if (me != null) {
            memberService.bumpTokenVersion(me.id());
        }

        ResponseCookie deleteCookie = createCookie("", 0); // 쿠키 삭제를 위해 수명을 0으로 설정

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // 캐시 방지
                .body(Map.of("success", true, "message", "로그아웃 성공"));
    }

    /**
     * HTTP 요청의 쿠키에서 특정 이름의 쿠키 값을 추출
     * 
     * @param req  HTTP 요청
     * @param name 찾을 쿠키 이름
     * @return 쿠키 값, 없으면 null
     */
    private String readCookie(HttpServletRequest req, String name) {
        var cs = req.getCookies();
        if (cs == null)
            return null;
        for (var c : cs) {
            if (name.equals(c.getName()))
                return c.getValue();
        }
        return null;
    }

    /**
     * 일관된 설정의 ResponseCookie를 생성합니다.
     * 
     * @param value  쿠키 값
     * @param maxAge 쿠키 수명(밀리초)
     * @return 생성된 ResponseCookie
     */
    private ResponseCookie createCookie(String value, long maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path(cookiePath)
                .maxAge(Duration.ofMillis(maxAge))
                .sameSite(sameSite);

        if (!cookieDomain.isBlank()) {
            builder.domain(cookieDomain);
        }

        return builder.build();
    }

    /**
     * 401 Unauthorized 응답과 함께 리프레시 토큰 쿠키를 삭제하는 응답을 생성합니다.
     * 인증 실패 또는 토큰 재사용 감지 시 사용됩니다.
     * 
     * @param message 오류 메시지
     * @return 쿠키 삭제와 함께 401 응답
     */
    private ResponseEntity<?> unauthorizedWithCookieDelete(String message) {
        ResponseCookie deleteCookie = createCookie("", 0); // 쿠키 수명 0으로 설정하여 즉시 삭제

        return ResponseEntity.status(401)
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store") // 캐시 방지
                .body(Map.of("success", false, "message", message));
    }
}