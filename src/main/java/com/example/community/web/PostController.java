package com.example.community.web;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;
import com.example.community.security.MemberDetails;
import com.example.community.service.PostService;
import com.example.community.service.PostLikeService;
import com.example.community.service.dto.PostDtos;
import com.example.community.util.PageableUtil;
import com.example.community.web.dto.PostRes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;
    private final PostLikeService postLikeService;

    /**
     * 게시글 생성 - 공지사항은 관리자만 작성 가능
     */
    @PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or @postSecurity.isNoticeAllowed(#req))")
    @PostMapping
    public ResponseEntity<PostRes> create(
            @AuthenticationPrincipal MemberDetails me,
            @Valid @RequestBody PostDtos.Create req
    ) {
        log.info("게시글 생성 요청: 작성자 ID={}, 게시판 유형={}", me.id(), req.boardType());
        
        try {
            Post saved = postService.create(me.id(), req);
            log.info("게시글 생성 완료: 게시글 ID={}, 제목={}", saved.getId(), saved.getTitle());
            
            return ResponseEntity.created(URI.create("/api/posts/" + saved.getId()))
                    .body(PostRes.of(saved));
        } catch (Exception e) {
            log.error("게시글 생성 실패: 작성자 ID={}, 오류={}", me.id(), e.getMessage());
            throw e;
        }
    }

    @GetMapping
    public ResponseEntity<Page<PostRes>> list(@RequestParam(required = false) String q, Pageable pageable) {
        log.info("게시글 목록 조회 요청: 검색어={} (제목 또는 내용)", q);
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> body = postService.search(q, safePageable).map(PostRes::of);
        log.info("게시글 목록 조회 완료: 총 {}건", body.getTotalElements());
        
        return ResponseEntity.ok(body);
    }
    
    // 추천수 기반 필터링 게시글 목록
    @GetMapping("/filter")
    public ResponseEntity<Page<PostRes>> listWithMinLikes(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "30") long minLikes,
            Pageable pageable
    ) {
        log.info("추천수 기반 게시글 목록 조회 요청: 검색어={} (제목 또는 내용), 최소추천수={}", q, minLikes);
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> body = postService.searchWithMinLikes(q, minLikes, safePageable).map(PostRes::of);
        log.info("추천수 기반 게시글 목록 조회 완료: 총 {}건", body.getTotalElements());
        
        return ResponseEntity.ok(body);
    }
    
    // 인기 게시글 (추천수 30 이상)
    @GetMapping("/popular")
    public ResponseEntity<Page<PostRes>> getPopular(Pageable pageable) {
        log.info("인기 게시글 목록 조회 요청");
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> body = postService.getPopularPosts(safePageable).map(PostRes::of);
        log.info("인기 게시글 목록 조회 완료: 총 {}건", body.getTotalElements());
        
        return ResponseEntity.ok(body);
    }
    
    // 베스트 게시글 (추천수 100 이상)
    @GetMapping("/best")
    public ResponseEntity<Page<PostRes>> getBest(Pageable pageable) {
        log.info("베스트 게시글 목록 조회 요청");
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> body = postService.getBestPosts(safePageable).map(PostRes::of);
        log.info("베스트 게시글 목록 조회 완료: 총 {}건", body.getTotalElements());
        
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostRes> get(@PathVariable Long id) {
        log.info("게시글 상세 조회 요청: 게시글 ID={}", id);
        
        try {
            // 조회수 증가와 함께 게시글 조회
            Post p = postService.getAndIncrementViewCount(id);
            log.info("게시글 상세 조회 완료: 게시글 ID={}, 제목={}", id, p.getTitle());
            
            return ResponseEntity.ok(PostRes.of(p));
        } catch (Exception e) {
            log.error("게시글 상세 조회 실패: 게시글 ID={}, 오류={}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * 게시글 수정 API
     * <p>게시글 작성자만 수정할 수 있습니다.</p>
     * <p>제목과 내용을 수정할 수 있습니다.</p>
     * 
     * @param id 게시글 ID
     * @param req 수정할 게시글 정보
     * @return 수정된 게시글 정보
     */
    @PreAuthorize("@postSecurity.isOwner(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<PostRes> update(
            @PathVariable Long id, 
            @Valid @RequestBody PostDtos.Update req,
            @AuthenticationPrincipal MemberDetails me
    ) {
        log.info("게시글 수정 요청: 게시글 ID={}, 요청자 ID={}", id, me.id());
        
        try {
            Post updated = postService.update(id, req);
            log.info("게시글 수정 완료: 게시글 ID={}, 제목={}", updated.getId(), updated.getTitle());
            
            return ResponseEntity.ok(PostRes.of(updated));
        } catch (Exception e) {
            log.error("게시글 수정 실패: 게시글 ID={}, 오류={}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * 게시글 삭제 API
     * <p>게시글 작성자 또는 관리자만 삭제할 수 있습니다.</p>
     * 
     * @param id 삭제할 게시글 ID
     * @return 204 No Content 응답
     */
    @PreAuthorize("hasRole('ADMIN') or @postSecurity.isOwner(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        log.info("게시글 삭제 요청: 게시글 ID={}, 요청자 ID={}", id, me.id());
        
        try {
            postService.delete(id);
            log.info("게시글 삭제 완료: 게시글 ID={}", id);
            
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("게시글 삭제 실패: 게시글 ID={}, 오류={}", id, e.getMessage());
            throw e;
        }
    }

    /**
     * 게시글 좋아요 토글 - 인증된 사용자만 가능
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        log.info("게시글 좋아요 토글 요청: 게시글 ID={}, 회원 ID={}", id, me.id());
        
        try {
            boolean liked = postLikeService.toggleLike(id, me.id());
            long likeCount = postLikeService.getLikeCount(id);
            
            log.info("게시글 좋아요 토글 완료: 게시글 ID={}, 좋아요 상태={}, 좋아요 수={}", id, liked, likeCount);
            
            return ResponseEntity.ok(Map.of(
                    "liked", liked,
                    "likeCount", likeCount
            ));
        } catch (Exception e) {
            log.error("게시글 좋아요 토글 실패: 게시글 ID={}, 오류={}", id, e.getMessage());
            throw e;
        }
    }

    @GetMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> getLikeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        log.info("게시글 좋아요 상태 조회 요청: 게시글 ID={}, 회원 ID={}", id, me != null ? me.id() : null);
        
        long likeCount = postLikeService.getLikeCount(id);
        boolean liked = me != null && postLikeService.isLikedByMember(id, me.id());
        
        log.info("게시글 좋아요 상태 조회 완료: 게시글 ID={}, 좋아요 상태={}, 좋아요 수={}", id, liked, likeCount);
        
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
        log.info("최근 {}일 내 추천순 게시글 조회 요청", days);
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> posts = postService.getRecentRecommended(days, safePageable)
                .map(PostRes::of);
        
        log.info("최근 {}일 내 추천순 게시글 조회 완료: 총 {}건", days, posts.getTotalElements());
        
        return ResponseEntity.ok(posts);
    }
    
    /**
     * 특정 게시판 타입의 게시글 목록 조회
     * GET /api/posts/board/{boardType}?q=검색어
     */
    @GetMapping("/board/{boardType}")
    public ResponseEntity<Page<PostRes>> getByBoardType(
            @PathVariable BoardType boardType,
            @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        log.info("게시판 타입별 게시글 목록 조회 요청: 게시판 타입={}, 검색어={} (제목 또는 내용)", boardType, q);
        
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        Page<PostRes> posts = postService.searchByBoardType(boardType, q, safePageable)
                .map(PostRes::of);
        
        log.info("게시판 타입별 게시글 목록 조회 완료: 게시판 타입={}, 총 {}건", boardType, posts.getTotalElements());
        
        return ResponseEntity.ok(posts);
    }
}
