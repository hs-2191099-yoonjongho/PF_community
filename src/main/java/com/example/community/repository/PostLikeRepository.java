package com.example.community.repository;

import com.example.community.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

/**
 * 게시글 좋아요 엔티티에 대한 데이터 접근 인터페이스
 * 좋아요 상태 확인 및 조회 기능을 제공합니다.
 */
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    /**
     * postId, memberId로 좋아요 삭제 (TOCTOU-safe)
     * 
     * @return 삭제된 row 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    int deleteByPostIdAndMemberId(Long postId, Long memberId);

    /**
     * postId, memberId로 좋아요 존재 여부
     */
    boolean existsByPostIdAndMemberId(Long postId, Long memberId);
}
