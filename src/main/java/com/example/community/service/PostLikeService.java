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

@Slf4j
@Service
@RequiredArgsConstructor
public class PostLikeService {
    private final PostLikeRepository postLikes;
    private final PostRepository posts;
    private final MemberRepository members;

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
            post.decrementLikeCount();
            return false;
        } else {
            // 좋아요 추가
            try {
                PostLike like = PostLike.builder()
                        .post(post)
                        .member(member)
                        .build();
                postLikes.save(like);
                post.incrementLikeCount();  // 저장 성공 후에만 증가
                return true;
            } catch (DataIntegrityViolationException e) {
                //  동시 요청으로 인한 중복 좋아요 시도 시 안전 처리
                log.debug("중복 좋아요 시도 감지: postId={}, memberId={}", postId, memberId);
                return true;  // 이미 좋아요 상태로 처리
            }
        }
    }

    @Transactional(readOnly = true)
    public long getLikeCount(Long postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        // Post 엔티티의 likeCount를 직접 반환 (성능 최적화)
        return post.getLikeCount();
    }

    @Transactional(readOnly = true)
    public boolean isLikedByMember(Long postId, Long memberId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("게시글", postId));
        Member member = members.findById(memberId)
                .orElseThrow(() -> new EntityNotFoundException("회원", memberId));
        return postLikes.existsByPostAndMember(post, member);
    }
}
