package com.example.community.service.mapper;

import com.example.community.domain.Post;
import com.example.community.domain.Member;
import com.example.community.domain.Comment;
import com.example.community.web.dto.PostRes;
import com.example.community.web.dto.MemberRes;
import com.example.community.web.dto.CommentRes;
import org.springframework.stereotype.Component;

/**
 * Entity와 DTO 간의 변환을 담당하는 매퍼
 */
@Component
public class DtoMapper {

    public PostRes toPostRes(Post post) {
        return new PostRes(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                toMemberRes(post.getAuthor()),
                post.getViewCount(),
                post.getLikeCount(),  // 🎯 likeCount 필드 추가
                post.getCreatedAt()
        );
    }

    public MemberRes toMemberRes(Member member) {
        return new MemberRes(
                member.getId(),
                member.getUsername(),
                member.getEmail()
        );
    }

    public CommentRes toCommentRes(Comment comment) {
        return new CommentRes(
                comment.getId(),
                comment.getContent(),
                toMemberRes(comment.getAuthor()),
                comment.getPost().getId(),
                comment.getCreatedAt()
        );
    }
}
