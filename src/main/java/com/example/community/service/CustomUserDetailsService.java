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
import java.util.stream.Collectors;

/**
 * Spring Security를 위한 사용자 인증 정보 로딩 서비스
 * 이메일 주소를 기반으로 사용자 정보를 조회하고 Spring Security가 사용할 UserDetails 객체를 생성합니다.
 * 탈퇴한 회원의 로그인 시도를 방지하는 로직이 포함되어 있습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository members;

    /**
     * 회원 id로 UserDetails 조회 (JWT id 기반 인증용)
     */
    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long memberId) throws UsernameNotFoundException {
        Member m = members.findById(memberId)
                .orElseThrow(() -> {
                    log.info("토큰 인증 실패: 존재하지 않는 회원 id - [MASKED]");
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다: id=[MASKED]");
                });
        if (!m.isActive()) {
            log.error("보안 경고: 탈퇴한 회원의 토큰 인증 시도 - id=[MASKED]");
            throw new DisabledException("탈퇴한 회원입니다");
        }
        var authorities = m.getRoles().stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new com.example.community.security.MemberDetails(m.getId(), m.getEmail(), m.getPassword(), authorities);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Member m = members.findByEmail(email)
                .orElseThrow(() -> {
                    log.info("로그인 실패: 존재하지 않는 이메일 - {}", email);
                    return new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email);
                });

        // 탈퇴한 회원 로그인 방지
        if (!m.isActive()) {
            log.error("보안 경고: 탈퇴한 회원의 로그인 시도 - {}", email);
            throw new DisabledException("탈퇴한 회원입니다");
        }

        var authorities = m.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
        return new MemberDetails(m.getId(), m.getEmail(), m.getPassword(), authorities);
    }
}