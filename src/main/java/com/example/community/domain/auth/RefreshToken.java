package com.example.community.domain.auth;

import com.example.community.domain.Member;
import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
    name = "refresh_tokens",
    indexes = {
        @Index(name = "idx_refresh_token_user_revoked_id", columnList = "user_id,revoked,id"),
        @Index(name = "idx_refresh_token_expires", columnList = "expires_at")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_token_hash", columnNames = "token_hash")
    }
)
public class RefreshToken extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 44)  // Base64 길이: 44자
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Member user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;
    
    // 비즈니스 메서드: 토큰 폐기
    public void revoke() {
        this.revoked = true;
    }
}