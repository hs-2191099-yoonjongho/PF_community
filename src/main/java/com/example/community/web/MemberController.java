package com.example.community.web;

import com.example.community.domain.Member;
import com.example.community.security.MemberDetails;
import com.example.community.service.MemberService;
import com.example.community.service.WithdrawalService;
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
        
        Member member = memberService.getByEmail(me.getUsername());
        log.info("내 정보 조회 완료: 회원 ID={}", me.id());
        return ResponseEntity.ok()
            .header("Cache-Control","no-store")
            .body(MemberDto.from(member));
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
        
        Member updated = memberService.updateUsername(me.id(), req.username());
        log.info("사용자명 변경 완료: 회원 ID={}, 새 사용자명={}", me.id(), updated.getUsername());
        return ResponseEntity.ok(MemberDto.from(updated));
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
        
        memberService.updatePassword(me.id(), req.currentPassword(), req.newPassword());
        log.info("비밀번호 변경 완료: 회원 ID={}", me.id());
        return ResponseEntity.ok().build();
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
        
        withdrawalService.withdrawMember(me.id(), req.password());
        log.info("회원 탈퇴 완료: 회원 ID={}", me.id());
        return ResponseEntity.noContent().build();
    }
}
