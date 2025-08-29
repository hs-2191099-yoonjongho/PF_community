package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.repository.RefreshTokenRepository;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.service.exception.WithdrawalException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 프로세스를 관리하는 서비스
 */
@Service
@RequiredArgsConstructor
public class WithdrawalService {
    private static final Logger log = LoggerFactory.getLogger(WithdrawalService.class);
    
    private final MemberRepository memberRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    
    /**
     * 회원 탈퇴 처리를 위한 통합 메소드:
     * 1. 비밀번호 확인
     * 2. 회원 계정 비활성화
     * 3. 작성 게시글의 작성자 정보 익명화
     * 4. 작성 댓글의 작성자 정보 익명화
     *
     * @param memberId 탈퇴할 회원 ID
     * @param password 비밀번호 확인용
     * @throws WithdrawalException 탈퇴 처리 중 오류 발생 시
     * @throws EntityNotFoundException 회원을 찾을 수 없는 경우
     */
    @Transactional
    public void withdrawMember(Long memberId, String password) {
        log.info("회원 ID {}의 탈퇴 처리를 시작합니다.", memberId);
        
        // 회원 조회
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));
        
        // 이미 탈퇴한 회원인지 확인
        if (!member.isActive()) {
            log.warn("회원 ID {}는 이미 탈퇴한 회원입니다.", memberId);
            throw WithdrawalException.alreadyWithdrawn(memberId);
        }
        
        // 관리자 계정은 탈퇴 방지 (선택적)
        if (member.hasRole("ROLE_ADMIN")) {
            log.warn("관리자 계정(ID: {})의 탈퇴가 시도되었습니다.", memberId);
            throw WithdrawalException.adminWithdrawalNotAllowed();
        }
        
        // 비밀번호 확인
        if (!passwordEncoder.matches(password, member.getPassword())) {
            log.warn("회원 ID {}의 탈퇴 처리 중 비밀번호 불일치 발생", memberId);
            throw WithdrawalException.invalidPassword();
        }
        
        // 회원 탈퇴 처리 - 상태 변경
        member.withdraw();
        
        // 개인정보 익명화
        anonymizePersonalInfo(member);
        log.info("회원 ID {}의 개인정보가 익명화되었습니다.", memberId);
        
        // 리프레시 토큰 폐기
        refreshTokenRepository.deleteAllByUserId(memberId);
        log.info("회원 ID {}의 리프레시 토큰이 폐기되었습니다.", memberId);
        
        // 작성 게시글의 작성자 정보 익명화
        int updatedPosts = anonymizePosts(memberId);
        log.info("회원 ID {}의 게시글 {}건이 익명화 처리되었습니다.", memberId, updatedPosts);
        
        // 작성 댓글의 작성자 정보 익명화
        int updatedComments = anonymizeComments(memberId);
        log.info("회원 ID {}의 댓글 {}건이 익명화 처리되었습니다.", memberId, updatedComments);
        
        log.info("회원 ID {}의 탈퇴 처리가 완료되었습니다.", memberId);
    }
    
    /**
     * 회원의 개인정보 익명화 처리
     */
    private void anonymizePersonalInfo(Member member) {
        String randomHash = UUID.randomUUID().toString();
        
        // 사용자명 익명화 (고유성 유지)
        member.setUsername("[탈퇴한 회원_" + member.getId() + "]");
        
        // 이메일 익명화 (RFC 2606 준수)
        member.setEmail("withdrawn_" + member.getId() + "@example.invalid");
        
        // 비밀번호를 랜덤 해시로 대체 (로그인 불가능하게)
        member.setPassword(passwordEncoder.encode(randomHash));
    }
    
    /**
     * 특정 회원이 작성한 모든 게시글을 익명화 처리
     */
    private int anonymizePosts(Long memberId) {
        return postRepository.markPostsByAuthorIdAsWithdrawn(memberId);
    }
    
    /**
     * 특정 회원이 작성한 모든 댓글을 익명화 처리
     */
    private int anonymizeComments(Long memberId) {
        return commentRepository.anonymizeByAuthorId(memberId);
    }
}
