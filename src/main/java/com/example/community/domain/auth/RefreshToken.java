package com.example.community.domain.auth;

import com.example.community.domain.Member;
import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 리프레시 토큰 엔티티 (DB 저장용)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_user_revoked_id", columnList = "user_id,revoked,id"),
        @Index(name = "idx_refresh_token_expires", columnList = "expires_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_token_hash", columnNames = "token_hash")
})
public class RefreshToken extends BaseTimeEntity {

    // PK
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토큰 해시값(Base64, 44자)
    @Column(name = "token_hash", nullable = false, length = 44)
    private String tokenHash;

    // 소유 사용자
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member user;

    // 만료 시각
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // 폐기 여부
    @Column(nullable = false)
    private boolean revoked;

    // 토큰 폐기 처리
    public void revoke() {
        this.revoked = true;
    }
}