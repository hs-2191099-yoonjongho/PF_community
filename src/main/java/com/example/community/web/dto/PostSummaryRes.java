package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.service.dto.PostSummaryDto;
import java.time.LocalDateTime;

/**
 * 게시글 목록 조회 시 사용하는 경량화된 응답 DTO
 */
public record PostSummaryRes(
        Long id,
        String title,
        String authorName, // null 대신 항상 빈 문자열 이상 보장
        long viewCount,
        long likeCount,
        BoardType boardType,
        String boardTypeDescription,
        LocalDateTime createdAt) {
    public static PostSummaryRes of(PostSummaryDto dto) {
        if (dto == null)
            throw new IllegalArgumentException("PostSummaryDto cannot be null");
        if (dto.id() == null)
            throw new IllegalArgumentException("Post id cannot be null");
        if (dto.title() == null)
            throw new IllegalArgumentException("Post title cannot be null");
        if (dto.createdAt() == null)
            throw new IllegalArgumentException("Post createdAt cannot be null");

        String authorName = java.util.Optional.ofNullable(dto.authorName())
                .map(String::trim).orElse(""); // 항상 빈 문자열 이상 보장
        String boardTypeDescription = dto.boardType() != null
                ? dto.boardType().getDescription()
                : ""; // 항상 빈 문자열 이상 보장

        return new PostSummaryRes(
                dto.id(),
                dto.title(),
                authorName,
                dto.viewCount(),
                dto.likeCount(),
                dto.boardType(),
                boardTypeDescription,
                dto.createdAt());
    }
}
