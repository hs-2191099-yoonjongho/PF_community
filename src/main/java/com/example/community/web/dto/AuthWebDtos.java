package com.example.community.web.dto;

import com.example.community.service.dto.AuthDtos;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Value;
import lombok.ToString;

/**
 * 인증 관련 웹 계층 DTO 클래스
 * 컨트롤러와 클라이언트 간 인증 관련 데이터 교환에 사용
 */
public class AuthWebDtos {
    /**
     * 회원가입 요청 DTO
     */
    @Value // 불변 객체로 변경
    @JsonIgnoreProperties(ignoreUnknown = true) // 알 수 없는 필드 무시
    @ToString(exclude = "password") // 비밀번호 로그 노출 방지
    public static class SignupRequest {
        @NotBlank(message = "사용자명은 필수입니다")
        @Size(min = 3, max = 30, message = "사용자명은 3~30자 이내여야 합니다")
        String username;

        @Email(message = "유효한 이메일 형식이어야 합니다")
        @NotBlank(message = "이메일은 필수입니다")
        String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        @Size(min = 8, max = 20, message = "비밀번호는 8~20자 이내여야 합니다")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // 직렬화 시 비밀번호 필드 제외
        String password;

        /**
         * Jackson 역직렬화를 위한 생성자
         * 입력값 정규화(trim) 처리 추가
         */
        @JsonCreator
        public SignupRequest(
                @JsonProperty("username") String username,
                @JsonProperty("email") String email,
                @JsonProperty("password") String password) {
            this.username = username != null ? username.trim() : null;
            this.email = email != null ? email.trim() : null;
            this.password = password != null ? password.trim() : null; // 비밀번호도 trim 처리
        }

        /**
         * 현재 웹 DTO를 서비스 계층 DTO로 변환
         * 
         * @return 서비스 계층에서 사용할 SignUp DTO
         */
        public AuthDtos.SignUp toServiceDto() {
            return new AuthDtos.SignUp(
                    this.username,
                    this.email,
                    this.password);
        }
    }

    /**
     * 로그인 요청 DTO
     */
    @Value // 불변 객체로 변경
    @JsonIgnoreProperties(ignoreUnknown = true) // 알 수 없는 필드 무시
    @ToString(exclude = "password") // 비밀번호 로그 노출 방지
    public static class LoginRequest {
        @Email(message = "유효한 이메일 형식이어야 합니다")
        @NotBlank(message = "이메일은 필수입니다")
        String email;

        @NotBlank(message = "비밀번호는 필수입니다")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // 직렬화 시 비밀번호 필드 제외
        String password;

        /**
         * Jackson 역직렬화를 위한 생성자
         * 입력값 정규화(trim) 처리 추가
         */
        @JsonCreator
        public LoginRequest(
                @JsonProperty("email") String email,
                @JsonProperty("password") String password) {
            this.email = email != null ? email.trim() : null;
            this.password = password != null ? password.trim() : null; // 비밀번호도 trim 처리
        }
    }

    /**
     * 토큰 응답 DTO
     * 응답 전용으로 사용되며 역직렬화는 필요 없음
     */
    @Value
    @JsonIgnoreProperties(ignoreUnknown = true) // 알 수 없는 필드 무시
    public static class TokenResponse {
        String accessToken;
    }
}
