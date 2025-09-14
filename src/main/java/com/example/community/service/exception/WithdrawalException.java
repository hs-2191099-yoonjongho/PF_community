package com.example.community.service.exception;

/**
 * 회원 탈퇴 처리 중 발생하는 예외
 */
public class WithdrawalException extends BusinessException {

    public WithdrawalException(String message) {
        super("WITHDRAWAL_ERROR", message);
    }

    // 비밀번호 불일치
    public static WithdrawalException invalidPassword() {
        return new WithdrawalException("비밀번호가 일치하지 않습니다");
    }

    // 이미 탈퇴한 회원
    public static WithdrawalException alreadyWithdrawn(Long memberId) {
        return new WithdrawalException("이미 탈퇴 처리된 회원입니다 (ID: " + memberId + ")");
    }

    // 관리자 계정은 탈퇴 불가
    public static WithdrawalException adminWithdrawalNotAllowed() {
        return new WithdrawalException("관리자 계정은 일반 탈퇴가 불가능합니다. 관리자에게 문의하세요");
    }
}
