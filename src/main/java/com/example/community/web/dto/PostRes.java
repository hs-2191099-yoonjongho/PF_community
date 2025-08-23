package com.example.community.web.dto;

import com.example.community.domain.Post;

public record PostRes(Long id, String title, String content, MemberRes author) {
    public static PostRes of(Post p) {
        return new PostRes(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                MemberRes.of(p.getAuthor())
        );
    }
}