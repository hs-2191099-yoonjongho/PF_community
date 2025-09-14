package com.example.community.repository.dto;

import com.example.community.domain.Member;

import java.time.LocalDateTime;

/**
 * 댓글 조회 시 사용하는 프로젝션 DTO
 * - N+1 문제 방지를 위한 최적화된 조회 결과 표현
 * - 엔티티 대신 필요한 데이터만 포함
 */
public record CommentProjection(
        Long id,
        String content,
        LocalDateTime createdAt,
        MemberDto author,
        Long postId) {
    /**
     * Member 엔티티의 필요 정보만 담는 내부 DTO
     */
    public record MemberDto(
            Long id,
            String username) {
        public static MemberDto from(Member member) {
            return new MemberDto(
                    member.getId(),
                    member.getUsername());
        }
    }
}
