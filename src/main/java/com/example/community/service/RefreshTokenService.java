package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Value("${refresh.exp-ms}")
    private long refreshExpMs;

    // 보안 고려한 토큰 생성
    public String issue(Member user) {
        // 1) 안전한 랜덤 토큰 생성
        String rawToken = UUID.randomUUID().toString();
        
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

    // 단순하지만 안전한 검증 (재사용 탐지 제거)
    public RefreshToken validateAndGet(String rawToken) {
        String hashedToken = hashToken(rawToken);
        var token = repo.findByTokenHashAndRevokedFalse(hashedToken).orElse(null);
        
        if (token == null || token.getExpiresAt().isBefore(Instant.now())) {
            return null;
        }
        
        return token;
    }

    // 단순한 회전
    public String rotate(RefreshToken oldToken) {
        oldToken.revoke();  // 💡 비즈니스 메서드 사용
        repo.save(oldToken);
        return issue(oldToken.getUser());
    }
    
    // 단순한 폐기
    public void revoke(String rawToken) {
        String hashedToken = hashToken(rawToken);
        repo.findByTokenHash(hashedToken).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();  // 💡 비즈니스 메서드 사용
                repo.save(token);
            }
        });
    }

    // 사용자별 모든 토큰 폐기
    public void revokeAll(Member user) {
        repo.bulkRevokeByUserId(user.getId());
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes); // Base64로 간단하게
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}