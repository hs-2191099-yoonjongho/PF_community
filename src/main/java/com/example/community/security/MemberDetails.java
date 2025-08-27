package com.example.community.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public record MemberDetails(
        Long id,                                    // ★ PK만 보관
        String username,
        String password,                            // JWT만 쓰면 null 가능
        Set<? extends GrantedAuthority> authorities
) implements UserDetails {

    public MemberDetails {
        // Compact constructor - 유효성 검증 및 불변 컬렉션 생성
        authorities = Set.copyOf(authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    // 계정 만료 여부: 기본 활성 처리
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // 계정 잠김 여부: 기본 활성 처리
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    // 자격 증명(비밀번호) 만료 여부: 기본 활성 처리
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // 계정 활성 여부: 기본 활성 처리 (탈퇴 등은 Service에서 제어)
    @Override
    public boolean isEnabled() {
        return true;
    }
}
