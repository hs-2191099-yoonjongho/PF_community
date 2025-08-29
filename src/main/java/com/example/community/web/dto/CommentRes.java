package com.example.community.web.dto;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;

import java.time.LocalDateTime;

public record CommentRes(Long id, String content, MemberRes author, Long postId, LocalDateTime createdAt) {
    /**
     * Comment 엔티티로부터 응답 DTO 생성
     */
    public static CommentRes of(Comment c) {
        return new CommentRes(
                c.getId(),
                c.getContent(),
                MemberRes.of(c.getAuthor()),
                c.getPost().getId(),
                c.getCreatedAt()
        );
    }
    
    /**
     * CommentProjection으로부터 응답 DTO 생성 (N+1 문제 해결)
     */
    public static CommentRes from(CommentProjection projection) {
        return new CommentRes(
                projection.id(),
                projection.content(),
                new MemberRes(projection.author().id(), projection.author().username(), null),
                projection.postId(),
                projection.createdAt() // 일관된 시간 필드 사용
        );
    }
}
