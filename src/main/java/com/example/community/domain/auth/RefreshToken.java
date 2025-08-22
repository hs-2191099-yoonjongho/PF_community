package com.example.community.domain.auth;

import com.example.community.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(indexes = {
        @Index(name="idx_refresh_user", columnList = "user_id")
})
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String tokenValue; // 원문 저장(포폴 수준: 단순), 실무는 해시 추천

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Member user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;
}