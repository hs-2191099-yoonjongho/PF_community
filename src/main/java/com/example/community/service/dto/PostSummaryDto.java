package com.example.community.service.dto;

import com.example.community.domain.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    List<ImageMeta> images
) {
    public static PostSummaryDto from(Post post) {
        List<ImageMeta> imageMetas = post.getImages().stream()
                .map(img -> new ImageMeta(
                        img.getFileKey(),
                        img.getUrl()))
                .collect(Collectors.toList());
                
        return new PostSummaryDto(
            post.getId(),
            post.getTitle(),
            post.getAuthor().getUsername(),
            post.getCreatedAt(),
            post.getViewCount(),
            post.getLikeCount(),
            imageMetas
        );
    }
    
    // 목록 변환 헬퍼 메서드
    public static List<PostSummaryDto> listFrom(List<Post> posts) {
        return posts.stream()
                .map(PostSummaryDto::from)
                .collect(Collectors.toList());
    }
}
