
package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.RefreshTokenRepository;
import com.example.community.service.dto.AuthDtos;
import com.example.community.service.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 관리 서비스
 * 회원 가입, 정보 조회, 계정 정보 업데이트 등을 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberService {
   
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * id로 토큰 버전 증가 (토큰 무효화)
     */
    @Transactional
    public void bumpTokenVersion(long memberId) {
        var m = members.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다: id=" + memberId));
        m.bumpTokenVersion();
        members.flush();
    }


    /**
     * ID로 회원 정보 Optional 조회 (컨트롤러 등에서 유연하게 사용)
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Member> findById(Long id) {
        if (id == null)
            return java.util.Optional.empty();
        return members.findById(id);
    }


    // ---- 입력 정규화/검증 유틸 ----
    private String requireNonBlankTrimmed(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다");
        }
        return value.trim();
    }

    private String requirePassword(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다");
        }
        return value;
    }

    private String requireAndNormalizeEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다");
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }


    /**
     * 사용자명 변경
     * 
     * @param memberId    회원 ID
     * @param newUsername 새 사용자명
     * @return 업데이트된 회원 정보
     */
    @Transactional
    public Member updateUsername(Long memberId, String newUsername) {
        String normalizedUsername = requireNonBlankTrimmed(newUsername, "사용자명").toLowerCase(java.util.Locale.ROOT);
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));
        if (member.getUsername().equalsIgnoreCase(normalizedUsername)) {
            return member;
        }
        try {
            member.updateUsername(normalizedUsername);
            members.flush(); // 유니크 제약 위반 예외를 여기서 catch
            return member;
        } catch (DataIntegrityViolationException e) {
            var cause = e.getCause();
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if ("UK_MEMBER_USERNAME".equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
                }
            }
            throw e;
        }
    }

    /**
     * 비밀번호 변경
     * 
     * @param memberId        회원 ID
     * @param currentPassword 현재 비밀번호
     * @param newPassword     새 비밀번호
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

        // 새 비밀번호가 기존과 동일한지 체크(최소 정책)
        if (passwordEncoder.matches(newPassword, member.getPassword())) {
            throw new IllegalArgumentException("이전과 동일한 비밀번호는 사용할 수 없습니다");
        }

        // 새 비밀번호 암호화 및 업데이트
        member.updatePassword(passwordEncoder.encode(newPassword));
        log.info("회원 ID {}의 비밀번호가 변경되었습니다", memberId);

        // 토큰 버전 증가 (모든 액세스 토큰 무효화)
        member.bumpTokenVersion();
        members.flush();
        log.info("회원 ID {}의 토큰 버전이 증가되었습니다. 모든 JWT 토큰이 무효화됩니다.", memberId);

        // 비밀번호 변경 시 리프레시 토큰 폐기 (보안 강화)
        refreshTokenRepository.deleteAllByUserId(memberId);
        log.info("회원 ID {}의 리프레시 토큰이 폐기되었습니다", memberId);

        return member;
    }

    /**
     * 회원 가입 처리
     * 
     * @param req 회원 가입 요청 정보 (사용자명, 이메일, 비밀번호)
     * @return 생성된 회원 정보
     * @throws IllegalArgumentException 이미 사용 중인 사용자명이나 이메일인 경우
     */
    @Transactional
    public Member signUp(AuthDtos.SignUp req) {
        String username = requireNonBlankTrimmed(req.username(), "사용자명").toLowerCase(java.util.Locale.ROOT);
        String email = requireAndNormalizeEmail(req.email());
        String password = requirePassword(req.password());
        try {
            Member m = Member.builder()
                    .username(username)
                    .email(email)
                    .password(passwordEncoder.encode(password))
                    .roles(new java.util.HashSet<>(java.util.Set.of("ROLE_USER")))
                    .build();
            Member saved = members.save(m);
            members.flush(); // 유니크 제약 위반 예외를 여기서 catch
            return saved;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            var cause = e.getCause();
            if (cause instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if ("UK_MEMBER_USERNAME".equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
                }
                if ("UK_MEMBER_EMAIL".equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
                }
            }
            throw e;
        }
    }
}
