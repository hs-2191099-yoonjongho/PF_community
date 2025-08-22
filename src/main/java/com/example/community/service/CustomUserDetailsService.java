package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository members;
/*
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member m = members.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        // Member 엔티티에 roles 컬렉션(예: ["ROLE_USER", "ROLE_ADMIN"])이 있다고 가정
        Set<GrantedAuthority> authorities = m.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return new User(m.getUsername(), m.getPassword(), authorities);
    }
}*/
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member m = members.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));

        Set<GrantedAuthority> authorities = m.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        return new User(m.getUsername(), m.getPassword(), authorities);
    }
}