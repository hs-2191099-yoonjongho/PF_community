package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.MemberRepository;
import com.example.community.service.MemberService;
import com.example.community.service.dto.AuthDtos;
import com.example.community.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;                 // ★ 추가
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;                        // ★ 추가
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager am;
    private final JwtUtil jwt;
    private final MemberRepository members;
    private final RefreshTokenService refreshTokenService;
    private final MemberService memberService;

    @Value("${refresh.exp-ms}") private long refreshExpMs;
    @Value("${refresh.cookie.name}") private String cookieName;      // 예: "refresh_token"
    @Value("${refresh.cookie.path}") private String cookiePath;      // 예: "/api/auth"
    @Value("${refresh.cookie.secure}") private boolean cookieSecure; // dev=false, prod=true
    @Value("${refresh.cookie.same-site}") private String sameSite;   // "Lax" | "None" | "Strict"
    @Value("${refresh.cookie.domain:}") private String cookieDomain; // ★ 선택(없으면 빈 문자열)

    /* 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {
        try {
            // DTO 변환하여 Service 로직 활용
            AuthDtos.SignUp dto = new AuthDtos.SignUp(
                req.getUsername(), 
                req.getEmail(), 
                req.getPassword()
            );
            
            memberService.signUp(dto);
            return ResponseEntity.ok("회원가입 성공");
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /* 로그인: Access 발급 + Refresh 쿠키 */
    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@Valid @RequestBody LoginReq req) {
        var auth = am.authenticate(new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
        var principal = (com.example.community.security.MemberDetails) auth.getPrincipal();

        Member user = members.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + principal.getUsername()));
        String access = jwt.generateAccessToken(principal.getUsername());
        String refreshRaw = refreshTokenService.issue(user);

        ResponseCookie.ResponseCookieBuilder lb = ResponseCookie.from(cookieName, refreshRaw)
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(Duration.ofMillis(refreshExpMs))
                .sameSite(sameSite);
        if (!cookieDomain.isBlank()) lb.domain(cookieDomain);        // ★ domain 적용(필요 시)
        ResponseCookie loginCookie = lb.build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, loginCookie.toString()) // ★ 상수 사용
                .body(new TokenRes(access));
    }

    /* 리프레시: 새 Access + (회전된) 새 Refresh 쿠키 */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(HttpServletRequest req) { // ★ @CookieValue → Request 직접 읽기
        // 1) 쿠키에서 refresh 읽기 (설정된 이름으로)
        String refreshRaw = readCookie(req, cookieName);              // ★ 이름 일관화
        if (refreshRaw == null || refreshRaw.isBlank())
            return ResponseEntity.status(401).build();

        // 2) 서버 저장소에서 유효성 검사(만료/폐기 여부 포함)
        RefreshToken current = refreshTokenService.validateAndGet(refreshRaw);
        if (current == null)
            return ResponseEntity.status(401).build();

        // 3) 회전(rotate): 기존 토큰 무효화 + 새 refresh 발급
        String newRaw = refreshTokenService.rotate(current);

        // 4) 새 access 발급
        String access = jwt.generateAccessToken(current.getUser().getEmail());

        // 5) 동일 속성으로 새 refresh 쿠키 설정
        ResponseCookie.ResponseCookieBuilder rb = ResponseCookie.from(cookieName, newRaw)
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(Duration.ofMillis(refreshExpMs))
                .sameSite(sameSite);
        if (!cookieDomain.isBlank()) rb.domain(cookieDomain);         // ★ domain 적용(필요 시)
        ResponseCookie rc = rb.build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rc.toString())
                .body(new TokenRes(access));
    }

    /* 로그아웃: Refresh 폐기 + 쿠키 삭제(동일 속성 + Max-Age=0) */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest req) {   // ★ @CookieValue 제거
        String refreshRaw = readCookie(req, cookieName);             // ★ 이름 일관화
        if (refreshRaw != null && !refreshRaw.isBlank()) {
            refreshTokenService.revoke(refreshRaw);
        }

        ResponseCookie.ResponseCookieBuilder db = ResponseCookie.from(cookieName, "")
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(0)                                           // ★ 삭제
                .sameSite(sameSite);
        if (!cookieDomain.isBlank()) db.domain(cookieDomain);        // ★ domain 적용(필요 시)
        ResponseCookie deleteCookie = db.build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .body("ok");
    }

    /* ===== DTOs ===== */
    @Data
    static class SignupRequest {
        @NotBlank private String username;
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    static class LoginReq {
        @Email @NotBlank private String email;
        @NotBlank private String password;
    }

    @Data
    static class TokenRes {
        private final String accessToken;
    }

    /* ===== 내부 유틸: 쿠키 읽기 ===== */
    private String readCookie(HttpServletRequest req, String name) {  // ★ 공통 유틸
        var cs = req.getCookies();
        if (cs == null) return null;
        for (var c : cs) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}