
package com.example.community.repository;

import org.springframework.data.jpa.repository.QueryHints;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 게시글 엔티티에 대한 데이터 접근 인터페이스
 * 게시글 조회, 검색, 필터링 등의 기능을 제공합니다.
 */
public interface PostRepository extends JpaRepository<Post, Long> {

        /**
         * like_count >= 1일 때만 안전하게 감소 (음수 방지)
         */
        @Modifying
        @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :postId")
        void decrementLikesSafely(@Param("postId") Long postId);

        /**
         * id+authorId로 PESSIMISTIC_WRITE 잠금 조회 (TOCTOU-safe)
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
        @Query("SELECT p FROM Post p WHERE p.id = :id AND p.author.id = :authorId")
        Optional<Post> findByIdAndAuthorIdForUpdate(@Param("id") Long id, @Param("authorId") Long authorId);

        /**
         * id와 authorId로 게시글 삭제 (TOCTOU-safe)
         * 
         * @return 삭제된 행 수
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        int deleteByIdAndAuthor_Id(Long id, Long authorId);

        /**
         * id로 PESSIMISTIC_WRITE 잠금 조회 (관리자 삭제용)
         */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
        @Query("SELECT p FROM Post p WHERE p.id = :id")
        Optional<Post> findByIdForUpdate(@Param("id") Long id);

        /**
         * 제목 또는 내용에 특정 검색어가 포함된 게시글을 조회합니다.
         * 작성자 정보를 함께 로딩하여 N+1 문제를 방지합니다.
         * 대소문자를 구분하지 않고 검색합니다.
         * 이 메서드는 기본 검색 메서드로 사용되며, 정렬은 Pageable 객체를 통해 지정할 수 있습니다.
         * 
         * @param query    검색어
         * @param pageable 페이징 정보 (정렬 조건 포함)
         * @return 검색 결과 페이지
         */
        @EntityGraph(attributePaths = "author")
        @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(CAST(p.content as string)) LIKE LOWER(CONCAT('%', :query, '%'))")
        Page<Post> findByTitleOrContentContainingIgnoreCaseWithAuthor(@Param("query") String query, Pageable pageable);

        /**
         * 특정 ID의 게시글을 작성자 정보 및 이미지와 함께 조회합니다.
         * 게시글 상세 페이지에서 사용되며, 작성자와 이미지를 함께 로딩하여 N+1 문제를 방지합니다.
         * 
         * @param id 게시글 ID
         * @return 게시글 (Optional)
         */
        @Query("SELECT DISTINCT p FROM Post p JOIN FETCH p.author LEFT JOIN FETCH p.images WHERE p.id = :id")
        Optional<Post> findByIdWithAuthorAndImages(@Param("id") Long id);

        /**
         * 특정 게시글의 조회수를 1 증가시킵니다.
         * 낙관적 락 충돌 방지를 위해 별도 쿼리로 처리합니다.
         * 
         * @param id 게시글 ID
         * @return 업데이트된 행 수
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
        int incrementViews(@Param("id") Long id);

        /**
         * 특정 게시판 타입의 게시글을 작성자 정보만 함께 로딩하여 조회합니다.
         * 요약 정보 조회용으로 사용됩니다.
         * 
         * @param boardType 게시판 타입
         * @param query     검색어 (선택적)
         * @param pageable  페이징 정보
         * @return 게시글 페이지 (작성자 포함)
         */
        @EntityGraph(attributePaths = "author")
        @Query("""
                SELECT p
                FROM Post p
                WHERE p.boardType = :boardType
                        AND (:query IS NULL OR :query = '' OR
                                (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :query, '%'))))
                                ORDER BY p.createdAt DESC, p.id DESC
                        """)
        Page<Post> findWithAuthorByBoardTypeAndQuery(
                        @Param("boardType") BoardType boardType,
                        @Param("query") String query,
                        Pageable pageable);
                        

        /**
         * 최근 N일 내 게시글을 추천수 기준으로 정렬하여 조회합니다.
         * 작성자 정보만 함께 로딩하여 N+1 문제를 방지합니다.
         * 이미지는 요약 정보에 포함되지 않으므로 로딩하지 않습니다.
         * 
         * @param from     시작일시
         * @param pageable 페이징 정보
         * @return 추천수 기준 정렬된 게시글 페이지
         */
        @EntityGraph(attributePaths = "author")
        @Query("""
                SELECT p
                FROM Post p
                WHERE p.createdAt >= :from
                ORDER BY p.likeCount DESC, p.createdAt DESC, p.id DESC
                        """)
        Page<Post> findRecentWithAuthorOrderByLikes(@Param("from") LocalDateTime from, Pageable pageable);

        /**
         * 특정 회원이 작성한 모든 게시글에 "[탈퇴한 회원]" 표시 추가
         * 회원 탈퇴 시 해당 회원의 게시글에 탈퇴 표시를 추가합니다.
         * 
         * @param memberId 회원 ID
         * @return 영향 받은 행 수
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE Post p SET p.title = CONCAT('[탈퇴한 회원] ', p.title) WHERE p.author.id = :memberId AND p.title NOT LIKE '[탈퇴한 회원]%'")
        int markPostsByAuthorIdAsWithdrawn(@Param("memberId") Long memberId);

        /**
         * 게시글 좋아요 수 증가 (동시성 문제 해결을 위한 직접 업데이트)
         * 낙관적 락 충돌을 방지하기 위해 별도 쿼리로 처리합니다.
         * 
         * @param id 게시글 ID
         * @return 업데이트된 행 수
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
        int incrementLikes(@Param("id") Long id);

        /**
         * 게시글 좋아요 수 감소 (동시성 문제 해결을 위한 직접 업데이트)
         * 낙관적 락 충돌을 방지하기 위해 별도 쿼리로 처리합니다.
         * 음수가 되지 않도록 보호합니다.
         * 
         * @param id 게시글 ID
         * @return 업데이트된 행 수
         */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE Post p SET p.likeCount = CASE WHEN p.likeCount > 0 THEN p.likeCount - 1 ELSE 0 END WHERE p.id = :id")
        int decrementLikes(@Param("id") Long id);

        /**
         * 게시글 요약 정보를 직접 조회하는 최적화된 쿼리
         * 불필요한 내용(content) 필드를 제외하고, 필요한 정보만 조회합니다.
         * 이미지는 요약 정보에 포함되지 않으므로 로딩하지 않습니다.
         * 
         * @param pageable 페이징 정보
         * @return 게시글 요약 DTO 페이지
         */
        @EntityGraph(attributePaths = "author")
        @Query("""
                SELECT p
                FROM Post p
                ORDER BY p.createdAt DESC, p.id DESC
                        """)
        Page<Post> findAllWithAuthor(Pageable pageable);

        /**
         * 특정 추천수 이상인 게시글의 요약 정보를 직접 조회하는 최적화된 쿼리
         * 이미지는 요약 정보에 포함되지 않으므로 로딩하지 않습니다.
         * 
         * @param minLikes 최소 추천수
         * @param query    검색어 (선택적)
         * @param pageable 페이징 정보
         * @return 게시글 페이지 (작성자 포함)
         */
        @EntityGraph(attributePaths = "author")
        @Query("""
                SELECT p
                FROM Post p
                WHERE p.likeCount >= :minLikes
                        AND (:query IS NULL OR :query = '' OR
                                (LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :query, '%'))))
                ORDER BY p.likeCount DESC, p.createdAt DESC, p.id DESC
                        """)
        Page<Post> findWithAuthorByLikeCountAndQuery(
                        @Param("query") String query,
                        @Param("minLikes") long minLikes,
                        Pageable pageable);
}
