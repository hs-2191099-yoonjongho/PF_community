package com.example.community.service;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.repository.dto.CommentProjection;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;
    private final PostRepository posts;
    private final MemberRepository members;

    @Transactional
    public Comment add(Long postId, Long authorId, String content) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        Member author = members.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("작성자", authorId));
        Comment c = Comment.builder().post(post).author(author).content(content).build();
        return comments.save(c);
    }
    
    /**
     * 게시글에 달린 댓글을 DTO 프로젝션으로 페이징하여 조회 (N+1 쿼리 문제 해결)
     * @param postId 게시글 ID
     * @param pageable 페이징 정보
     * @return 페이징된 댓글 프로젝션 목록
     */
    @Transactional(readOnly = true)
    public Page<CommentProjection> getProjectionsByPostWithPaging(Long postId, Pageable pageable) {
        // 게시글 존재 여부 확인
        if (!posts.existsById(postId)) {
            throw new EntityNotFoundException("게시글", postId);
        }
        
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafeCommentPageable(pageable);
        
        // DTO 프로젝션 사용하여 조회 (N+1 문제 해결)
        return comments.findProjectionsByPostId(postId, safePageable);
    }

    @Transactional
    public void delete(Long commentId) { 
        if (!comments.existsById(commentId)) {
            throw new EntityNotFoundException("삭제할 댓글", commentId);
        }
        comments.deleteById(commentId); 
    }
}
