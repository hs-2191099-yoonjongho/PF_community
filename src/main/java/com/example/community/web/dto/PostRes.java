package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record PostRes(
        Long id, 
        String title, 
        String content, 
        MemberRes author, 
        long viewCount, 
        long likeCount, 
        BoardType boardType, 
        String boardTypeDescription,
        List<PostImageRes> images,
        LocalDateTime createdAt
) {
    public static PostRes of(Post p) {
        return new PostRes(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                MemberRes.of(p.getAuthor()),
                p.getViewCount(),
                p.getLikeCount(),
                p.getBoardType(),
                p.getBoardType().getDescription(),
                p.getImages().stream()
                    .map(PostImageRes::of)
                    .collect(Collectors.toList()),
                p.getCreatedAt()
        );
    }
}