package com.example.community.web.dto;

import com.example.community.domain.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;

public record MemberDto(
                Long id,
                String username,
                String email,
                Set<String> roles,
                boolean active,
                LocalDateTime createdAt,
                LocalDateTime updatedAt) {
        /**
         * Member 엔티티로부터 DTO를 생성합니다.
         * 
         * @param member 변환할 회원 엔티티
         * @return 회원 정보가 담긴 DTO
         * @throws IllegalArgumentException member가 null인 경우
         */
        public static MemberDto from(Member member) {
                if (member == null) {
                        throw new IllegalArgumentException("Member cannot be null");
                }

                return new MemberDto(
                                member.getId(),
                                member.getUsername(),
                                member.getEmail(),
                                member.getRoles(),
                                member.isActive(),
                                member.getCreatedAt(),
                                member.getUpdatedAt());
        }

        /**
         * 사용자명 변경 요청 DTO
         */
        public record UpdateUsername(
                        @NotBlank(message = "사용자명은 필수입니다") @Size(min = 3, max = 30, message = "사용자명은 3~30자 이내여야 합니다") String username) {
        }

        /**
         * 비밀번호 변경 요청 DTO
         */
        public record UpdatePassword(
                        @NotBlank(message = "현재 비밀번호는 필수입니다") String currentPassword,

                        @NotBlank(message = "새 비밀번호는 필수입니다") @Size(min = 8, max = 20, message = "비밀번호는 8~20자 이내여야 합니다") String newPassword) {
        }

        /**
         * 회원 탈퇴 요청 DTO
         */
        public record WithdrawalRequest(
                        @NotBlank(message = "비밀번호는 필수입니다") String password) {
        }
}
