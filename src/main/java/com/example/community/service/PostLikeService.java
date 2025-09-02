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
     * @param postId 게시글 ID
     * @param memberId 회원 ID
     * @return 좋아요 상태 (true: 좋아요 추가됨, false: 좋아요 취소됨)
     * @throws EntityNotFoundException 게시글이나 회원이 존재하지 않는 경우
     */
    @Transactional
    public boolean toggleLike(Long postId, Long memberId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));

        var existingLike = postLikes.findByPostAndMember(post, member);
        
        if (existingLike.isPresent()) {
            // 좋아요 취소
            postLikes.delete(existingLike.get());
            // 엔티티 메서드 대신 직접 DB 업데이트를 통해 동시성 문제 해결
            posts.decrementLikes(postId);
            return false;
        } else {
            // 좋아요 추가
            try {
                PostLike like = PostLike.builder()
                        .post(post)
                        .member(member)
                        .build();
                postLikes.save(like);
                // 엔티티 메서드 대신 직접 DB 업데이트를 통해 동시성 문제 해결
                posts.incrementLikes(postId);
                return true;
            } catch (DataIntegrityViolationException e) {
                //  동시 요청으로 인한 중복 좋아요 시도 시 안전 처리
                log.debug("중복 좋아요 시도 감지: postId={}, memberId={}", postId, memberId);
                return true;  // 이미 좋아요 상태로 처리
            }
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
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        // Post 엔티티의 likeCount를 직접 반환 (성능 최적화)
        return post.getLikeCount();
    }

    /**
     * 회원의 게시글 좋아요 여부 확인
     * 
     * @param postId 게시글 ID
     * @param memberId 회원 ID
     * @return 좋아요 여부 (true: 좋아요 상태, false: 좋아요 아님)
     * @throws EntityNotFoundException 게시글이나 회원이 존재하지 않는 경우
     */
    @Transactional(readOnly = true)
    public boolean isLikedByMember(Long postId, Long memberId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));
        return postLikes.existsByPostAndMember(post, member);
    }
}
