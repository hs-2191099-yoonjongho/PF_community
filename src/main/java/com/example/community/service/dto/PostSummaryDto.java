package com.example.community.service.dto;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;

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
    BoardType boardType
) {
    /**
     * Post 엔티티로부터 요약 DTO 생성
     * @param post 게시글 엔티티
     * @return 요약 정보만 포함한 DTO (이미지 제외)
     */
    public static PostSummaryDto from(Post post) {
        if (post == null) {
            throw new IllegalArgumentException("Post cannot be null");
        }
                
        return new PostSummaryDto(
            post.getId(),
            post.getTitle(),
            post.getAuthor() != null ? post.getAuthor().getUsername() : "알 수 없음",
            post.getCreatedAt(),
            post.getViewCount(),
            post.getLikeCount(),
            post.getBoardType()
        );
    }
}
