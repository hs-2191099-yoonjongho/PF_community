package com.example.community.service;

import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import com.example.community.service.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository posts;
    private final MemberRepository members;

    @Transactional
    public Post create(Long authorId, PostDtos.Create req) {
        Member author = members.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("작성자", authorId));
        Post p = Post.builder()
                .author(author)
                .title(req.title())
                .content(req.content())
                .viewCount(0)
                .build();
        return posts.save(p);
    }

    @Transactional(readOnly = true)
    public Page<Post> search(String q, Pageable pageable) {
        return (q == null || q.isBlank())
                ? posts.findAll(pageable)
                : posts.findByTitleContainingIgnoreCaseWithAuthor(q, pageable);
    }
    
    // 추천수 기반 필터링 검색
    @Transactional(readOnly = true)
    public Page<Post> searchWithMinLikes(String q, long minLikes, Pageable pageable) {
        return (q == null || q.isBlank())
                ? posts.findByLikeCountGreaterThanEqual(minLikes, pageable)
                : posts.findByLikeCountGreaterThanEqualAndTitleContaining(minLikes, q, pageable);
    }
    
    // 인기 게시글만 조회 (추천수 30 이상)
    @Transactional(readOnly = true)
    public Page<Post> getPopularPosts(Pageable pageable) {
        return posts.findByLikeCountGreaterThanEqual(30L, pageable);
    }
    
    // 베스트 게시글만 조회 (추천수 100 이상)
    @Transactional(readOnly = true)
    public Page<Post> getBestPosts(Pageable pageable) {
        return posts.findByLikeCountGreaterThanEqual(100L, pageable);
    }

    @Transactional(readOnly = true)
    public Post get(Long id) { 
        return posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글", id));
    }

    @Transactional
    public Post getAndIncrementViewCount(Long id) {
        Post post = posts.findByIdWithAuthor(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글", id));
        
        // 별도 쿼리로 조회수 증가 (낙관적 락 충돌 방지)
        posts.incrementViews(id);
        
        // 메모리상 객체도 증가 (응답 일관성 보장)
        post.incrementViewCount();
        
        return post;
    }

    @Transactional
    public Post update(Long id, PostDtos.Update req) {
        Post p = posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("수정할 게시글", id));
        p.updateContent(req.title(), req.content());  // 💡 비즈니스 메서드 사용
        return p;
    }

    @Transactional
    public void delete(Long id) { posts.deleteById(id); }
    
    /**
     * 최근 N일 내 추천순 게시글 조회
     * @param days 조회할 일수 (1~365)
     * @param pageable 페이징 정보
     * @return 추천순 게시글 목록
     */
    @Transactional(readOnly = true)
    public Page<Post> getRecentRecommended(int days, Pageable pageable) {
        // 날짜 범위 검증
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365, but was: " + days);
        }
        
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return posts.findRecentOrderByLikes(from, pageable);
    }
}
