package com.example.community.web.dto;

import com.example.community.domain.Comment;

public record CommentRes(Long id, String content, MemberRes author, Long postId) {
    public static CommentRes of(Comment c) {
        return new CommentRes(
                c.getId(),
                c.getContent(),
                MemberRes.of(c.getAuthor()),
                c.getPost().getId()
        );
    }
}
