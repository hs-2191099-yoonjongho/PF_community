package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.RefreshTokenRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repo;

    @Value("${refresh.exp-ms}")
    private long refreshExpMs;

    private static final SecureRandom RNG = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public String issue(Member user) {
        byte[] buf = new byte[32];
        RNG.nextBytes(buf);
        String raw = B64.encodeToString(buf);

        RefreshToken rt = RefreshToken.builder()
                .tokenValue(raw)
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpMs))
                .revoked(false)
                .build();
        repo.save(rt);

        return raw;
    }

    public RefreshToken validateAndGet(String raw) {
        return repo.findByTokenValue(raw)
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElse(null);
    }

    public String rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        repo.save(oldToken);
        return issue(oldToken.getUser());
    }
    @Transactional
    public void revoke(String raw) {
        // 만약 validateAndGet이 만료/폐기 토큰을 null로 반환한다면,
        // 여기서는 직접 찾는 편이 안전합니다. (만료여도 레코드는 revoke 하고 싶을 수 있으니까)
        repo.findByTokenValue(raw).ifPresent(t -> {
            if (!t.isRevoked()) {
                t.setRevoked(true);
                repo.save(t);
            }
        });
    }

    public void revokeAll(Member user) {
        repo.findAll().forEach(t -> {
            if (t.getUser().getId().equals(user.getId()) && !t.isRevoked()) {
                t.setRevoked(true);
                repo.save(t);
            }
        });
    }
}