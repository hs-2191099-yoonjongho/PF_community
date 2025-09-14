package com.example.community.web.dto;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;

import java.time.LocalDateTime;

public record CommentRes(Long id, String content, MemberRes author, Long postId, LocalDateTime createdAt) {
    /**
     * Comment 엔티티로부터 응답 DTO 생성
     * 
     * @throws IllegalArgumentException Comment 또는 필수 관계 엔티티가 null인 경우
     */
    public static CommentRes of(Comment c) {
        if (c == null) {
            throw new IllegalArgumentException("Comment cannot be null");
        }
        if (c.getAuthor() == null) {
            throw new IllegalArgumentException("Comment author cannot be null");
        }
        if (c.getPost() == null) {
            throw new IllegalArgumentException("Comment post cannot be null");
        }

        return new CommentRes(
                c.getId(),
                c.getContent(),
                MemberRes.of(c.getAuthor()),
                c.getPost().getId(),
                c.getCreatedAt());
    }

    /**
     * CommentProjection으로부터 응답 DTO 생성
     * 
     * @throws IllegalArgumentException CommentProjection 또는 필수 데이터가 null인 경우
     */
    public static CommentRes from(CommentProjection projection) {
        if (projection == null) {
            throw new IllegalArgumentException("CommentProjection cannot be null");
        }
        if (projection.author() == null) {
            throw new IllegalArgumentException("Comment author cannot be null in projection");
        }

        return new CommentRes(
                projection.id(),
                projection.content(),
                new MemberRes(projection.author().id(), projection.author().username(), null),
                projection.postId(),
                projection.createdAt());
    }
}
