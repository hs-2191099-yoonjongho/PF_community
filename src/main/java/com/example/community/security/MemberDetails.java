package com.example.community.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;


 // 인증된 회원 정보를 Spring Security에서 사용하기 위한 클래스

public record MemberDetails(
    Long id,                                    // ★ PK만 보관
    String username,                              // 이메일 주소 저장
    String password,                            // JWT만 쓰면 null 가능
    Set<? extends GrantedAuthority> authorities
) implements UserDetails {



    public MemberDetails {
        // Compact constructor - null 방어 및 불변 컬렉션 생성
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    /**
     * PK 반환 (id()와 동일, 가독성용)
     */
    public Long getId() {
        return id();
    }

    /**
     * 주어진 역할(권한)이 있는지 확인
     */
    public boolean hasRole(String role) {
        if (role == null) return false;
        return authorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }

    /**
     * 사용자의 권한 목록 반환
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 사용자의 식별자 (이메일) 반환
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 사용자의 비밀번호 반환 (JWT 인증에서는 거의 사용되지 않음)
     */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * 계정 만료 여부: 기본 활성 처리
     * 실제 만료 처리는 서비스 레이어에서 수행
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 계정 잠김 여부: 기본 활성 처리
     * 실제 잠금 처리는 서비스 레이어에서 수행
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 자격 증명(비밀번호) 만료 여부: 기본 활성 처리
     * 실제 비밀번호 만료 정책은 서비스 레이어에서 관리
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 계정 활성 여부: 기본 활성 처리
     * 실제 회원 상태(탈퇴 등)는 CustomUserDetailsService에서 확인
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * toString()에서 password가 로그에 노출되지 않도록 마스킹 처리
     */
    @Override
    public String toString() {
        return "MemberDetails[" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", password=****" + // 비밀번호는 항상 마스킹
                ", authorities=" + authorities +
                ']';
    }
}
