package com.example.community.web;

import com.example.community.domain.Post;
import com.example.community.security.MemberDetails;
import com.example.community.service.PostService;
import com.example.community.service.PostLikeService;
import com.example.community.service.dto.PostDtos;
import com.example.community.web.dto.PostRes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;
    private final PostLikeService postLikeService;

    @PostMapping
    public ResponseEntity<PostRes> create(
            @AuthenticationPrincipal MemberDetails me,
            @Valid @RequestBody PostDtos.Create req
    ) {
        Post saved = postService.create(me.id(), req);
        return ResponseEntity.created(URI.create("/api/posts/" + saved.getId()))
                .body(PostRes.of(saved));
    }

    @GetMapping
    public ResponseEntity<Page<PostRes>> list(@RequestParam(required = false) String q, Pageable pageable) {
        Page<PostRes> body = postService.search(q, pageable).map(PostRes::of);
        return ResponseEntity.ok(body);
    }
    
    // 추천수 기반 필터링 게시글 목록
    @GetMapping("/filter")
    public ResponseEntity<Page<PostRes>> listWithMinLikes(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") long minLikes,
            Pageable pageable
    ) {
        Page<PostRes> body = postService.searchWithMinLikes(q, minLikes, pageable).map(PostRes::of);
        return ResponseEntity.ok(body);
    }
    
    // 인기 게시글 (추천수 30 이상)
    @GetMapping("/popular")
    public ResponseEntity<Page<PostRes>> getPopular(Pageable pageable) {
        Page<PostRes> body = postService.getPopularPosts(pageable).map(PostRes::of);
        return ResponseEntity.ok(body);
    }
    
    // 베스트 게시글 (추천수 100 이상)
    @GetMapping("/best")
    public ResponseEntity<Page<PostRes>> getBest(Pageable pageable) {
        Page<PostRes> body = postService.getBestPosts(pageable).map(PostRes::of);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostRes> get(@PathVariable Long id) {
        // 조회수 증가와 함께 게시글 조회
        Post p = postService.getAndIncrementViewCount(id);
        return ResponseEntity.ok(PostRes.of(p));
    }

    @PreAuthorize("@postSecurity.isOwner(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<PostRes> update(@PathVariable Long id, @Valid @RequestBody PostDtos.Update req) {
        Post updated = postService.update(id, req);
        return ResponseEntity.ok(PostRes.of(updated));
    }

    @PreAuthorize("hasRole('ADMIN') or @postSecurity.isOwner(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        boolean liked = postLikeService.toggleLike(id, me.id());
        long likeCount = postLikeService.getLikeCount(id);
        
        return ResponseEntity.ok(Map.of(
                "liked", liked,
                "likeCount", likeCount
        ));
    }

    @GetMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> getLikeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        long likeCount = postLikeService.getLikeCount(id);
        boolean liked = me != null && postLikeService.isLikedByMember(id, me.id());
        
        return ResponseEntity.ok(Map.of(
                "liked", liked,
                "likeCount", likeCount
        ));
    }
    
    /**
     * 최근 N일 내 추천순 게시글 조회
     * GET /api/posts/recommended?days=7&page=0&size=10
     */
    @GetMapping("/recommended")
    public ResponseEntity<Page<PostRes>> getRecentRecommended(
            @RequestParam(defaultValue = "7") int days,
            Pageable pageable
    ) {
        Page<PostRes> posts = postService.getRecentRecommended(days, pageable)
                .map(PostRes::of);
        return ResponseEntity.ok(posts);
    }
}
