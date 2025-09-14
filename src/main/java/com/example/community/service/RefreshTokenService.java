
package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.auth.RefreshToken;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.RefreshTokenRepository;
import com.example.community.service.exception.TokenReuseDetectedException;
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
    private final MemberRepository memberRepository;

    @Value("${refresh.exp-ms}")
    private long refreshExpMs;

    /**
    * memberId로 리프레시 토큰 발급 (서비스 캡슐화)
    * @param memberId 회원 ID
     * @return 발급된 원본 리프레시 토큰
     * @throws IllegalArgumentException 회원이 존재하지 않을 때
     */
    public String issueByUserId(long memberId) {
        Member user = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다: id=" + memberId));
        return issue(user);
    }


    /**
     * 안전한 리프레시 토큰 발급
     * @param user 토큰을 발급할 사용자
     * @return 원본 토큰 (클라이언트에게 전달)
     */
    private String issue(Member user) {
        // 1) 강화된 암호학적 랜덤 토큰 생성 (32바이트 = 256비트)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // 2) 보안을 위해 해시값으로 저장 (DB 유출 시 원본 토큰 보호)
        String hashedToken = hashToken(rawToken);

        RefreshToken rt = RefreshToken.builder()
                .tokenHash(hashedToken) // 해시값 저장
                .user(user)
                .expiresAt(Instant.now().plusMillis(refreshExpMs))
                .revoked(false)
                .build();
        repo.save(rt);

        return rawToken; // 클라이언트에는 원본 반환
    }

    /**
     * 리프레시 토큰 검증 (단일 조회, 경합 안전)
     * @param rawToken 클라이언트가 제공한 원본 토큰
     * @return 유효한 토큰 객체 또는 null (유효하지 않은 경우)
     * @throws TokenReuseDetectedException 이미 폐기된 토큰이 재사용될 경우 발생
     * @throws IllegalArgumentException 토큰이 누락된 경우
     */
    @Transactional(readOnly = true)
    public RefreshToken validateAndGet(String rawToken) {
        if (rawToken == null || rawToken.isBlank())
            throw new IllegalArgumentException("토큰 누락");
        String hash = hashToken(rawToken);
        var token = repo.findByTokenHash(hash).orElse(null);
        if (token == null)
            return null;
        if (token.isRevoked())
            throw new TokenReuseDetectedException("폐기된 토큰 재사용 감지");
        if (token.getExpiresAt().isBefore(Instant.now()))
            return null;
        return token;
    }

    /**
     * 리프레시 토큰 교체 (CAS 기반, 경쟁 조건 안전, 선조회 후 CAS)
     * @param rawOldToken 기존 원본 토큰 문자열
     * @return 새로 발급된 원본 토큰
     * @throws TokenReuseDetectedException 폐기된/유효하지 않은 토큰 재사용 시
     * @throws IllegalArgumentException 토큰이 누락된 경우
     */
    public String rotate(String rawOldToken) {
        if (rawOldToken == null || rawOldToken.isBlank())
            throw new IllegalArgumentException("토큰 누락");
        String hash = hashToken(rawOldToken);
        // 1) 먼저 사용자 얻기 (없으면 바로 실패)
        var tokenOpt = repo.findByTokenHash(hash);
        if (tokenOpt.isEmpty())
            throw new TokenReuseDetectedException("폐기된/유효하지 않은 토큰 재사용 감지");
        Member user = tokenOpt.get().getUser();
        // 2) CAS로 활성 → 폐기 전환
        int updated = repo.markRevokedIfActive(hash);
        if (updated == 0)
            throw new TokenReuseDetectedException("폐기된/유효하지 않은 토큰 재사용 감지");
        // 3) 새 토큰 발급
        return issue(user);
    }

    /**
     * 단일 리프레시 토큰 폐기
     * @param rawToken 폐기할 원본 토큰
     */
    public void revoke(String rawToken) {
        String hashedToken = hashToken(rawToken);
        repo.findByTokenHash(hashedToken).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke(); // 비즈니스 메서드 사용
                repo.save(token);
            }
        });
    }

    /**
     * 토큰 해싱 처리 (SHA-256 + Base64)
     * 원본 토큰을 해시하여 DB에 저장하기 위한 메서드
     * @param rawToken 원본 토큰
     * @return 해시된 토큰 값 (Base64 인코딩)
     * @throws IllegalStateException SHA-256 사용 불가 시
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