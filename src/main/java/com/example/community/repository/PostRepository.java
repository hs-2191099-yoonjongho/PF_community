package com.example.community.repository;

import com.example.community.domain.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {
    
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Post> findByTitleContainingIgnoreCaseWithAuthor(@Param("query") String query, Pageable pageable);
    
    @EntityGraph(attributePaths = "author")
    Page<Post> findAll(Pageable pageable);
    
    @Query("SELECT p FROM Post p JOIN FETCH p.author WHERE p.id = :id")
    Optional<Post> findByIdWithAuthor(@Param("id") Long id);
    
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    int incrementViews(@Param("id") Long id);
    
    boolean existsByIdAndAuthor_Id(Long postId, Long authorId);
    
    // 추천수 기반 필터링 쿼리들
    @EntityGraph(attributePaths = "author")
    @Query("SELECT p FROM Post p WHERE p.likeCount >= :minLikes ORDER BY p.likeCount DESC, p.createdAt DESC")
    Page<Post> findByLikeCountGreaterThanEqual(@Param("minLikes") long minLikes, Pageable pageable);
    
    @EntityGraph(attributePaths = "author") 
    @Query("SELECT p FROM Post p WHERE p.likeCount >= :minLikes AND LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY p.likeCount DESC, p.createdAt DESC")
    Page<Post> findByLikeCountGreaterThanEqualAndTitleContaining(@Param("minLikes") long minLikes, @Param("query") String query, Pageable pageable);
    
    // 최근 N일 내 추천순 정렬 (서브쿼리 방식)
    @EntityGraph(attributePaths = "author")
    @Query("""
        SELECT p FROM Post p 
        WHERE p.createdAt >= :from 
        ORDER BY (
            SELECT COUNT(pl.id) 
            FROM PostLike pl 
            WHERE pl.post = p
        ) DESC, p.createdAt DESC
        """)
    Page<Post> findRecentOrderByLikes(@Param("from") LocalDateTime from, Pageable pageable);
}
