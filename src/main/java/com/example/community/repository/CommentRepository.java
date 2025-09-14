package com.example.community.repository;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


/**
 * 댓글 엔티티에 대한 데이터 접근 인터페이스
 * 댓글 조회, 작성자 확인, 익명화 등의 기능을 제공합니다.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 댓글 id와 작성자 id가 모두 일치할 때만 삭제 (TOCTOU 방지)
     * 
     * @param id       댓글 id
     * @param authorId 작성자 id
     * @return 삭제된 row 수
     */
    int deleteByIdAndAuthor_Id(Long id, Long authorId);

    /**
     * 게시글 ID로 DTO 프로젝션을 사용하여 최적화된 댓글 조회
     * - N+1 쿼리 문제 방지
     * - 필요한 데이터만 선택적으로 조회
     * 
     * @param postId   게시글 ID
     * @param pageable 페이징 정보
     * @return 댓글 프로젝션 페이지
     */
    @Query("""
            SELECT new com.example.community.repository.dto.CommentProjection(
                c.id, c.content, c.createdAt,
                new com.example.community.repository.dto.CommentProjection$MemberDto(c.author.id, c.author.username),
                c.post.id)
            FROM Comment c
            WHERE c.post.id = :postId
            """)
    Page<CommentProjection> findProjectionsByPostId(@Param("postId") Long postId, Pageable pageable);

    /**
     * 특정 회원이 작성한 모든 댓글에서 내용을 익명화
     * 회원 탈퇴 시 사용됩니다.
     * 
     * @param memberId 회원 ID
     * @return 영향 받은 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Comment c SET c.content = '[삭제된 댓글입니다]' WHERE c.author.id = :memberId")
    int anonymizeByAuthorId(@Param("memberId") Long memberId);

    /**
     * 관리자: 댓글 ID로 벌크 삭제 (select 없이 바로 delete)
     * 
     * @param id 댓글 ID
     * @return 삭제된 row 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.id = :id")
    int deleteByIdBulk(@Param("id") Long id);
}
