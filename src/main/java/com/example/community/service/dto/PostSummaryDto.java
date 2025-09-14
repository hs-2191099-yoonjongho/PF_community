package com.example.community.service.dto;

import com.example.community.domain.BoardType;

import java.time.LocalDateTime;

/**
 * 게시글 목록 조회용 요약 DTO
 * 게시글 목록 조회 시 본문 내용을 제외한 요약 정보만 포함하여 성능 최적화
 */
public record PostSummaryDto(
        Long id,
        String title,
        String authorName,
        LocalDateTime createdAt,
        long viewCount,
        long likeCount,
        BoardType boardType) {
    /**
     * 필드 기반 팩토리 메서드 (엔티티 의존성 제거)
     */
    public static PostSummaryDto from(
            Long id,
            String title,
            String authorName,
            LocalDateTime createdAt,
            long viewCount,
            long likeCount,
            BoardType boardType) {
        if (id == null)
            throw new IllegalArgumentException("id cannot be null");
        if (createdAt == null)
            throw new IllegalArgumentException("createdAt cannot be null");
        return new PostSummaryDto(id, title, authorName, createdAt, viewCount, likeCount, boardType);
    }
}
