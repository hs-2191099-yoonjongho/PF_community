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
}
