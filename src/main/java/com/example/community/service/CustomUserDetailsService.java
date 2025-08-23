package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository members;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member m = members.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + username));
        var authorities = m.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return new MemberDetails(m.getId(), m.getUsername(), m.getPassword(), authorities);
    }
}