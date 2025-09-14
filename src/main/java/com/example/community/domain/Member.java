package com.example.community.domain;

import com.example.community.domain.support.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Set;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "members", indexes = {
        @Index(name = "uk_member_username", columnList = "username", unique = true),
        @Index(name = "uk_member_email", columnList = "email", unique = true)
})
public class Member extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role")
    private Set<String> roles;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true; // 활성 상태 (탈퇴 시 false)

    @Column(name = "withdrawal_date")
    private LocalDateTime withdrawalDate; // 탈퇴일시

    @Column(nullable = false)
    @Builder.Default
    private int tokenVersion = 0; // 토큰 버전 (로그아웃, 탈퇴 시 증가)

    @Version
    private Long version; // 낙관적 잠금을 위한 버전 필드

    // 권한 보유 여부 확인 메소드
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    // 사용자명 변경 메소드
    public void updateUsername(String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            throw new IllegalArgumentException("사용자명은 필수입니다");
        }
        if (newUsername.length() > 30) {
            throw new IllegalArgumentException("사용자명은 30자를 초과할 수 없습니다");
        }
        this.username = newUsername;
    }

    // 비밀번호 변경 메소드
    public void updatePassword(String newPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다");
        }
        this.password = newPassword;
    }

    // 회원 탈퇴 처리 메소드 - 상태 변경만 수행
    public void withdraw() {
        this.active = false;
        this.withdrawalDate = LocalDateTime.now();
    }

    // 활성 상태 확인 메서드
    public boolean isActive() {
        return this.active;
    }

    // 개인정보 수정을 위한 메서드들 - 탈퇴 처리용
    public void setUsername(String username) {
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // 토큰 버전 증가 메소드 (모든 토큰 무효화)
    public void bumpTokenVersion() {
        this.tokenVersion++;
    }
}
