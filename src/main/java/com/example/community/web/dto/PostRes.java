package com.example.community.web.dto;

import com.example.community.domain.Post;

import java.time.LocalDateTime;

public record PostRes(Long id, String title, String content, MemberRes author, long viewCount, long likeCount, LocalDateTime createdAt) {
    public static PostRes of(Post p) {
        return new PostRes(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                MemberRes.of(p.getAuthor()),
                p.getViewCount(),
                p.getLikeCount(),  // 추천수 필드 추가
                p.getCreatedAt()
        );
    }
}