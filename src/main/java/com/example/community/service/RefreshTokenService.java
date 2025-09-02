package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.service.exception.TokenReuseDetectedException;
import com.example.community.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * 리프레시 토큰 관리 서비스
 * 토큰 발급, 검증, 갱신 및 폐기 기능을 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Value("${refresh.exp-ms}")
    private long refreshExpMs;

    /**
     * 안전한 리프레시 토큰 발급
     * @param user 토큰을 발급할 사용자
     * @return 원본 토큰 (클라이언트에게 전달)
     */
    public String issue(Member user) {
        // 1) 강화된 암호학적 랜덤 토큰 생성 (32바이트 = 256비트)
        byte[] tokenBytes = new byte[32];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(tokenBytes);
        
        // URL-safe Base64 인코딩 (쿠키나 헤더에서 안전하게 사용 가능)
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        
        // 2) 보안을 위해 해시값으로 저장 (DB 유출 시 원본 토큰 보호)
        String hashedToken = hashToken(rawToken);

        RefreshToken rt = RefreshToken.builder()
                .tokenHash(hashedToken)  // 해시값 저장
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpMs))
                .revoked(false)
                .build();
        repo.save(rt);

        return rawToken; // 클라이언트에는 원본 반환
    }

    /**
     * 리프레시 토큰 검증
     * @param rawToken 클라이언트가 제공한 원본 토큰
     * @return 유효한 토큰 객체 또는 null (유효하지 않은 경우)
     * @throws TokenReuseDetectedException 이미 폐기된 토큰이 재사용될 경우 발생
     */
    @Transactional(readOnly = true)
    public RefreshToken validateAndGet(String rawToken) {
        String hashedToken = hashToken(rawToken);
        
        // 1) 먼저 토큰 자체가 존재하는지 확인 (revoked 상태 무관)
        var anyToken = repo.findByTokenHash(hashedToken).orElse(null);
        
        // 2) 토큰이 존재하지만 이미 폐기된 경우 - 재사용 공격 감지
        if (anyToken != null && anyToken.isRevoked()) {
            throw new TokenReuseDetectedException("폐기된 토큰의 재사용이 감지되었습니다. 보안 위협 가능성이 있습니다.");
        }
        
        // 3) 활성 토큰인지 확인 (사용 가능한 토큰만)
        var token = repo.findByTokenHashAndRevokedFalse(hashedToken).orElse(null);
        
        // 4) 토큰은 있지만 만료된 경우
        if (token != null && token.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        
        return token;
    }

    /**
     * 리프레시 토큰 교체 (이전 토큰 폐기 후 새 토큰 발급)
     * @param oldToken 기존 토큰 객체
     * @return 새로 발급된 원본 토큰
     */
    public String rotate(RefreshToken oldToken) {
        oldToken.revoke();  // 비즈니스 메서드 사용
        repo.save(oldToken);
        return issue(oldToken.getUser());
    }
    
    /**
     * 단일 리프레시 토큰 폐기
     * @param rawToken 폐기할 원본 토큰
     */
    public void revoke(String rawToken) {
        String hashedToken = hashToken(rawToken);
        repo.findByTokenHash(hashedToken).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();  // 비즈니스 메서드 사용
                repo.save(token);
            }
        });
    }

    /**
     * 사용자의 모든 리프레시 토큰 폐기 (로그아웃 전체 기기)
     * @param user 사용자 객체
     */
    public void revokeAll(Member user) {
        repo.bulkRevokeByUserId(user.getId());
    }

    /**
     * 토큰 해싱 처리 (SHA-256 + Base64)
     * 원본 토큰을 해시하여 DB에 저장하기 위한 메서드
     * 
     * @param rawToken 원본 토큰
     * @return 해시된 토큰 값 (Base64 인코딩)
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
        }
    }
}