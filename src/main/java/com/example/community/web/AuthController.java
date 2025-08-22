package com.example.community.web;

import com.example.community.config.JwtUtil;
import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.MemberRepository;
import com.example.community.service.RefreshTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager am;
    private final JwtUtil jwt;
    private final MemberRepository members;     // ← 필드명 일관
    private final PasswordEncoder encoder;      // ← 필드명 일관
    private final RefreshTokenService refreshService;

    @Value("${refresh.exp-ms}") private long refreshExpMs;
    @Value("${refresh.cookie.name}") private String cookieName;
    @Value("${refresh.cookie.path}") private String cookiePath;
    @Value("${refresh.cookie.secure}") private boolean cookieSecure;
    @Value("${refresh.cookie.same-site}") private String sameSite;

    /* 회원가입 */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest req) {
        if (members.findByUsername(req.getUsername()).isPresent())
            return ResponseEntity.badRequest().body("이미 존재하는 사용자명입니다.");
        if (members.existsByEmail(req.getEmail()))
            return ResponseEntity.badRequest().body("이미 존재하는 이메일입니다.");

        Member m = Member.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .password(encoder.encode(req.getPassword()))
                // ↓ roles 컬렉션에 기본 권한 추가 (ROLE_ 접두사 포함)
                .roles(new java.util.HashSet<>(java.util.List.of("ROLE_USER")))
                .build();

        members.save(m);
        return ResponseEntity.ok("회원가입 성공");
    }

    /* 로그인: Access 발급 + Refresh 쿠키 */
    @PostMapping("/login")
    public ResponseEntity<TokenRes> login(@Valid @RequestBody LoginReq req) {
        var auth = am.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
        var principal = (org.springframework.security.core.userdetails.User) auth.getPrincipal();

        Member user = members.findByUsername(principal.getUsername()).orElseThrow();
        String access = jwt.generateAccessToken(user.getUsername());
        String refreshRaw = refreshService.issue(user);

        ResponseCookie rc = ResponseCookie.from(cookieName, refreshRaw)
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(Duration.ofMillis(refreshExpMs))
                .sameSite(sameSite)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", rc.toString())
                .body(new TokenRes(access));
    }

    /* 리프레시: 새 Access + (로테이션된) 새 Refresh 쿠키 */
    @PostMapping("/refresh")
    public ResponseEntity<TokenRes> refresh(
            @CookieValue(name = "${refresh.cookie.name}", required = false) String refreshRaw) { // ← 프로퍼티 사용
        if (refreshRaw == null) return ResponseEntity.status(401).build();

        RefreshToken current = refreshService.validateAndGet(refreshRaw);
        if (current == null) return ResponseEntity.status(401).build();

        String newRaw = refreshService.rotate(current);
        String access = jwt.generateAccessToken(current.getUser().getUsername());

        ResponseCookie rc = ResponseCookie.from(cookieName, newRaw)
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(Duration.ofMillis(refreshExpMs))
                .sameSite(sameSite)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", rc.toString())
                .body(new TokenRes(access));
    }

    /* 로그아웃: Refresh 폐기 + 쿠키 삭제 */
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @CookieValue(name = "${refresh.cookie.name}", required = false) String refreshRaw) { // ← 프로퍼티 사용
        if (refreshRaw != null) {
            // 서비스에 위임해서 확실히 저장/영속되게
            refreshService.revoke(refreshRaw);
        }

        ResponseCookie rc = ResponseCookie.from(cookieName, "")
                .httpOnly(true).secure(cookieSecure).path(cookiePath)
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        return ResponseEntity.ok()
                .header("Set-Cookie", rc.toString())
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
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data
    static class TokenRes {
        private final String accessToken;
    }



    @GetMapping("/whoami")
    public java.util.Map<String, Object> whoami() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return java.util.Map.of(
                "principal", auth == null ? null : auth.getName(),
                "authorities", auth == null ? null : auth.getAuthorities()
        );
    }
}