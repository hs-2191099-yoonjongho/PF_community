package com.example.community.repository;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /**
     * 게시글 ID로 DTO 프로젝션을 사용하여 최적화된 댓글 조회
     * - N+1 쿼리 문제 방지
     * - 필요한 데이터만 선택적으로 조회
     */
    @Query("SELECT new com.example.community.repository.dto.CommentProjection(" +
           "c.id, c.content, c.createdAt, " +
           "new com.example.community.repository.dto.CommentProjection$MemberDto(c.author.id, c.author.username), " +
           "c.post.id) " +
           "FROM Comment c " +
           "WHERE c.post.id = :postId")
    Page<CommentProjection> findProjectionsByPostId(@Param("postId") Long postId, Pageable pageable);
    
    @Query("select c.author.id from Comment c where c.id = :id")
    Optional<Long> findAuthorIdById(@Param("id") Long id);
    
    /**
     * 특정 회원이 작성한 모든 댓글에서 내용을 익명화
     * @param memberId 회원 ID
     * @return 영향 받은 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.content = '[삭제된 댓글입니다]' WHERE c.author.id = :memberId")
    int anonymizeByAuthorId(@Param("memberId") Long memberId);
}
