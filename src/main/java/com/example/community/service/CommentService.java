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

/**
 * 댓글 관련 비즈니스 로직을 처리하는 서비스 클래스
 * 댓글 작성, 조회, 삭제 등의 기능을 제공하며 트랜잭션 관리를 담당합니다.
 * 성능 최적화를 위해 조회 시 DTO 프로젝션을 활용합니다.
 */
@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;
    private final PostRepository posts;
    private final MemberRepository members;

    /**
     * 댓글 작성을 위한 서비스 메서드
     * 
     * @param postId 댓글이 작성될 게시글 ID
     * @param authorId 댓글 작성자 ID
     * @param content 댓글 내용
     * @return 저장된 댓글 엔티티
     * @throws EntityNotFoundException 게시글이나 작성자가 존재하지 않는 경우
     */
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


    /**
     * 댓글을 삭제하는 서비스 메서드
     * @param commentId 삭제할 댓글 ID
     * @throws EntityNotFoundException 댓글이 존재하지 않는 경우
     */
    @Transactional
    public void delete(Long commentId) { 
        // 댓글 존재 여부 확인
        if (!comments.existsById(commentId)) {
            throw new EntityNotFoundException("삭제할 댓글", commentId);
        }
        
        // 실제 삭제 수행
        // 참고: 권한 검증은 @PreAuthorize 어노테이션을 통해 컨트롤러 단에서 처리됨
        comments.deleteById(commentId); 
    }
}
