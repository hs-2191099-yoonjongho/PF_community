package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.domain.PostLike;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostLikeRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 좋아요 관련 서비스
 * 좋아요 추가/취소, 좋아요 수 조회, 좋아요 상태 확인 기능을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {
    
    private final PostLikeRepository postLikes;
    private final PostRepository posts;
    private final MemberRepository members;

    /**
     * 게시글 좋아요 토글 (추가/취소)
     * 
     * @param postId   게시글 ID
     * @param memberId 회원 ID
     * @return 좋아요 상태 (true: 좋아요 추가됨, false: 좋아요 취소됨)
     * @throws EntityNotFoundException 게시글이나 회원이 존재하지 않는 경우
     */
    @Transactional
    public boolean toggleLike(Long postId, Long memberId) {
        log.info("[좋아요 토글] postId={}, memberId={}", postId, memberId);
        // 존재 검증만 필요하면 existsById
        if (!posts.existsById(postId)) {
            log.error("[좋아요 토글] 존재하지 않는 게시글: postId={}", postId);
            throw new EntityNotFoundException("게시글", postId);
        }
        if (!members.existsById(memberId)) {
            log.error("[좋아요 토글] 존재하지 않는 회원: memberId={}", memberId);
            throw new EntityNotFoundException("회원", memberId);
        }

        // 좋아요 취소 시도: 존재 조회 없이 원샷 삭제, 삭제 행수로 분기
        int deleted = postLikes.deleteByPostIdAndMemberId(postId, memberId);
        if (deleted == 1) {
            posts.decrementLikesSafely(postId); // like_count >= 1 일 때만 감소
            return false;
        }

        // 좋아요 추가 시도
        log.info("[좋아요 토글] save 시도: postId={}, memberId={}", postId, memberId);
        try {
            Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));
            Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
            PostLike like = PostLike.builder()
                    .post(post)
                    .member(member)
                    .build();
            postLikes.save(like);
            posts.incrementLikes(postId);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.error("[좋아요 토글] DataIntegrityViolationException 발생: {}", e.getMessage(), e);
            // 유니크 위반만 true 반환, FK 등은 전파
            if (isUniqueViolation(e, "uq_post_member")) {
                log.debug("중복 좋아요 시도 감지: postId={}, memberId={}", postId, memberId);
                return true;
            }
            throw e;
        }
    }

    /**
     * 게시글의 좋아요 수 조회
     * 
     * @param postId 게시글 ID
     * @return 좋아요 수
     * @throws EntityNotFoundException 게시글이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public long getLikeCount(Long postId) {
        // Post 엔티티 로딩 대신 likeCount만 조회해도 되지만, 기존 방식 유지
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        return post.getLikeCount();
    }

    /**
     * 회원의 게시글 좋아요 여부 확인
     * 
     * @param postId   게시글 ID
     * @param memberId 회원 ID
     * @return 좋아요 여부 (true: 좋아요 상태, false: 좋아요 아님)
     * @throws EntityNotFoundException 게시글이나 회원이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public boolean isLikedByMember(Long postId, Long memberId) {
        if (!posts.existsById(postId))
            throw new EntityNotFoundException("게시글", postId);
        if (!members.existsById(memberId))
            throw new EntityNotFoundException("회원", memberId);
        return postLikes.existsByPostIdAndMemberId(postId, memberId);
    }

    /**
     * DataIntegrityViolationException이 유니크 제약 위반(uq_post_member)인지 판별
     */
    private static boolean isUniqueViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains(constraintName))
                return true;
            // SQLState 23505는 표준 유니크 위반
            if (cause instanceof org.hibernate.exception.ConstraintViolationException) {
                String sqlState = ((org.hibernate.exception.ConstraintViolationException) cause).getSQLState();
                if ("23505".equals(sqlState))
                    return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
