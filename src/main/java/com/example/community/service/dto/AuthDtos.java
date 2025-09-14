package com.example.community.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 인증 관련 서비스 계층 DTO 클래스
 * 서비스 계층에서 사용되는 인증 관련 데이터 전송 객체
 */
public class AuthDtos {
    /**
     * 회원가입을 위한 서비스 계층 DTO
     * 
     * @param username 사용자 이름 (3-30자)
     * @param email    이메일 주소 (유효한 이메일 형식)
     * @param password 비밀번호 (8-100자)
     */
    public record SignUp(
            @NotBlank(message = "사용자명은 필수입니다") @Size(min = 3, max = 30, message = "사용자명은 3~30자 이내여야 합니다") String username,

            @Email(message = "유효한 이메일 형식이어야 합니다") @NotBlank(message = "이메일은 필수입니다") String email,

            @NotBlank(message = "비밀번호는 필수입니다") @Size(min = 8, max = 100, message = "비밀번호는 8~100자 이내여야 합니다") String password) {
    }

}
