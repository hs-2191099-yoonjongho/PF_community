package com.example.community.web.dto;

import com.example.community.domain.Member;

/**
 * 회원 정보를 위한 응답 DTO
 * 클라이언트에 전송할 회원 기본 정보를 포함
 */
public record MemberRes(Long id, String username, String email) {
    /**
     * Member 엔티티로부터 응답 DTO 생성
     *
     * @param m 변환할 회원 엔티티
     * @return 회원 정보가 담긴 DTO
     * @throws IllegalArgumentException member가 null인 경우
     */
    public static MemberRes of(Member m) {
        if (m == null) {
            throw new IllegalArgumentException("Member cannot be null");
        }
        return new MemberRes(m.getId(), m.getUsername(), m.getEmail());
    }
}