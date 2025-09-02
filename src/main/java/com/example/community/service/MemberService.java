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
     * 회원 가입 처리
     * @param req 회원 가입 요청 정보 (사용자명, 이메일, 비밀번호)
     * @return 생성된 회원 정보
     * @throws IllegalArgumentException 이미 사용 중인 사용자명이나 이메일인 경우
     */
    @Transactional
    public Member signUp(AuthDtos.SignUp req) {
        // 입력값 정리 (trim 처리)
        String username = req.username().trim();
        String email = req.email().trim();
        String password = req.password(); // 비밀번호는 trim 하지 않음 (공백도 유효한 문자로 취급)
        
        // 사용자명 중복 검사
        if (members.existsByUsername(username)) 
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        
        // 이메일 중복 검사
        if (members.existsByEmail(email)) 
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다");
        
        // 회원 엔티티 생성 및 저장
        Member m = Member.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .roles(Set.of("ROLE_USER"))
                .build();
        
        return members.save(m);
    }

    /**
     * 사용자명으로 회원 정보 조회
     * @param username 조회할 사용자명
     * @return 회원 정보
     * @throws EntityNotFoundException 회원을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public Member getByUsername(String username) {
        String trimmedUsername = username != null ? username.trim() : username;
        return members.findByUsername(trimmedUsername).orElseThrow(() -> 
            new EntityNotFoundException("사용자명으로 회원을 찾을 수 없습니다: " + trimmedUsername));
    }
    
    /**
     * 이메일로 회원 정보 조회
     * @param email 조회할 이메일
     * @return 회원 정보
     * @throws EntityNotFoundException 회원을 찾을 수 없는 경우
     */
    @Transactional(readOnly = true)
    public Member getByEmail(String email) {
        String trimmedEmail = email != null ? email.trim() : email;
        return members.findByEmail(trimmedEmail).orElseThrow(() -> 
            new EntityNotFoundException("이메일로 회원을 찾을 수 없습니다: " + trimmedEmail));
    }
    
    /**
     * 사용자명 변경
     * @param memberId 회원 ID
     * @param newUsername 새 사용자명
     * @return 업데이트된 회원 정보
     */
    @Transactional
    public Member updateUsername(Long memberId, String newUsername) {
        String trimmedUsername = newUsername != null ? newUsername.trim() : newUsername;
        
        // null 체크 추가
        if (trimmedUsername == null || trimmedUsername.isEmpty()) {
            throw new IllegalArgumentException("사용자명은 필수입니다");
        }
        
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));

        // 현재 사용자명과 동일한 경우 불필요한 업데이트 방지
        if (member.getUsername().equals(trimmedUsername)) {
            return member;
        }

        // 이미 사용중인 사용자명인지 확인
        if (members.existsByUsername(trimmedUsername)) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다");
        }

        member.updateUsername(trimmedUsername);
        log.info("회원 ID {}의 사용자명이 '{}'로 변경되었습니다", memberId, trimmedUsername);
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
        
        // 토큰 버전 증가 (모든 액세스 토큰 무효화)
        member.bumpTokenVersion();
        log.info("회원 ID {}의 토큰 버전이 증가되었습니다. 모든 JWT 토큰이 무효화됩니다.", memberId);

        return member;
    }
}
