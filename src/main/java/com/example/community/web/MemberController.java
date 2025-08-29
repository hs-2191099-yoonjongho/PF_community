package com.example.community.web;

import com.example.community.domain.Member;
import com.example.community.security.MemberDetails;
import com.example.community.service.MemberService;
import com.example.community.service.WithdrawalService;
import com.example.community.service.exception.WithdrawalException;
import com.example.community.web.dto.MemberDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/members")
public class MemberController {
    
    private final MemberService memberService;
    private final WithdrawalService withdrawalService;
    
    /**
     * 내 정보 조회 API
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<MemberDto> getMyInfo(@AuthenticationPrincipal MemberDetails me) {
        log.info("내 정보 조회 요청: 회원 ID={}", me.id());
        
        try {
            Member member = memberService.getByEmail(me.getUsername());
            log.info("내 정보 조회 완료: 회원 ID={}", me.id());
            return ResponseEntity.ok(MemberDto.from(member));
        } catch (Exception e) {
            log.error("내 정보 조회 실패: 회원 ID={}, 오류={}", me.id(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 사용자명 변경 API
     */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/username")
    public ResponseEntity<MemberDto> updateUsername(
            @AuthenticationPrincipal MemberDetails me,
            @Valid @RequestBody MemberDto.UpdateUsername req
    ) {
        log.info("사용자명 변경 요청: 회원 ID={}, 새 사용자명={}", me.id(), req.username());
        
        try {
            Member updated = memberService.updateUsername(me.id(), req.username());
            log.info("사용자명 변경 완료: 회원 ID={}, 새 사용자명={}", me.id(), updated.getUsername());
            return ResponseEntity.ok(MemberDto.from(updated));
        } catch (Exception e) {
            log.error("사용자명 변경 실패: 회원 ID={}, 오류={}", me.id(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 비밀번호 변경 API
     */
    @PreAuthorize("isAuthenticated()")
    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @AuthenticationPrincipal MemberDetails me,
            @Valid @RequestBody MemberDto.UpdatePassword req
    ) {
        log.info("비밀번호 변경 요청: 회원 ID={}", me.id());
        
        try {
            memberService.updatePassword(me.id(), req.currentPassword(), req.newPassword());
            log.info("비밀번호 변경 완료: 회원 ID={}", me.id());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("비밀번호 변경 실패: 회원 ID={}, 오류={}", me.id(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * 회원 탈퇴 API
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal MemberDetails me,
            @Valid @RequestBody MemberDto.WithdrawalRequest req
    ) {
        log.info("회원 탈퇴 요청: 회원 ID={}", me.id());
        
        try {
            withdrawalService.withdrawMember(me.id(), req.password());
            log.info("회원 탈퇴 완료: 회원 ID={}", me.id());
            return ResponseEntity.noContent().build();
        } catch (WithdrawalException e) {
            // WithdrawalException은 @ExceptionHandler에서 따로 처리
            log.error("회원 탈퇴 실패 (비즈니스 예외): 회원 ID={}, 코드={}, 메시지={}", 
                    me.id(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("회원 탈퇴 실패: 회원 ID={}, 오류={}", me.id(), e.getMessage());
            throw e;
        }
    }
}
