package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.RefreshTokenRepository;
import com.example.community.service.dto.AuthDtos;
import com.example.community.service.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public Member signUp(AuthDtos.SignUp req) {
        if (members.existsByUsername(req.username())) throw new IllegalArgumentException("username already used");
        if (members.existsByEmail(req.email())) throw new IllegalArgumentException("email already used");
        Member m = Member.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .roles(Set.of("ROLE_USER"))
                .build();
        return members.save(m);
    }

    @Transactional(readOnly = true)
    public Member getByUsername(String username) {
        return members.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("member not found"));
    }
    
    @Transactional(readOnly = true)
    public Member getByEmail(String email) {
        return members.findByEmail(email).orElseThrow(() -> 
            new EntityNotFoundException("이메일로 회원을 찾을 수 없습니다: " + email));
    }
    
    /**
     * 사용자명 변경
     * @param memberId 회원 ID
     * @param newUsername 새 사용자명
     * @return 업데이트된 회원 정보
     */
    @Transactional
    public Member updateUsername(Long memberId, String newUsername) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));

        // 현재 사용자명과 동일한 경우 불필요한 업데이트 방지
        if (member.getUsername().equals(newUsername)) {
            return member;
        }

        // 이미 사용중인 사용자명인지 확인
        if (members.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        }

        member.updateUsername(newUsername);
        log.info("회원 ID {}의 사용자명이 '{}'로 변경되었습니다", memberId, newUsername);
        return member;
    }

    /**
     * 비밀번호 변경
     * @param memberId 회원 ID
     * @param currentPassword 현재 비밀번호
     * @param newPassword 새 비밀번호
     * @return 업데이트된 회원 정보
     */
    @Transactional
    public Member updatePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다");
        }

        // 새 비밀번호 암호화 및 업데이트
        member.updatePassword(passwordEncoder.encode(newPassword));
        log.info("회원 ID {}의 비밀번호가 변경되었습니다", memberId);

        // 비밀번호 변경 시 리프레시 토큰 폐기 (보안 강화)
        refreshTokenRepository.deleteAllByUserId(memberId);
        log.info("회원 ID {}의 리프레시 토큰이 폐기되었습니다", memberId);

        return member;
    }
}
