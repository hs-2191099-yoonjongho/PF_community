package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.security.MemberDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final MemberRepository members;

    @Override
    @Transactional(readOnly = true) 
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member m = members.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
        
        // 탈퇴한 회원 로그인 방지
        if (!m.isActive()) {
            log.warn("탈퇴한 회원의 로그인 시도: {}", email);
            throw new DisabledException("탈퇴한 회원입니다");
        }
        
        var authorities = m.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return new MemberDetails(m.getId(), m.getEmail(), m.getPassword(), authorities);
    }
}