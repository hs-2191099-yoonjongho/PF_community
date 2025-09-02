package com.example.community.web;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;
import com.example.community.security.MemberDetails;
import com.example.community.service.PostLikeService;
import com.example.community.service.PostService;
import com.example.community.service.dto.PostDtos;
import com.example.community.util.PageableUtil;
import com.example.community.web.dto.PostRes;
import com.example.community.web.dto.PostSummaryRes;
import com.example.community.web.dto.PostWebDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * 게시글 관련 API를 처리하는 컨트롤러
 * 게시글 생성, 조회, 수정, 삭제 및 좋아요 기능을 제공합니다.
 * 다양한 조건에 따른 게시글 목록 조회 기능도 포함합니다.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/posts")
@Validated
public class PostController {
    private final PostService postService;
    private final PostLikeService postLikeService;

    /**
     * 게시글 생성 API
     * 인증된 사용자만 접근 가능하며, 관리자 또는 해당 게시판 유형에 권한이 있는 사용자만 사용 가능
     */
    @PreAuthorize("isAuthenticated() and (hasRole('ADMIN') or @postSecurity.isBoardTypeAllowed(#req.boardType()))")
    @PostMapping
    public ResponseEntity<PostRes> create(
            @AuthenticationPrincipal MemberDetails me,
            @P("req") @Valid @RequestBody PostWebDtos.CreateRequest req
    ) {
        log.info("게시글 생성 요청: 작성자 ID={}, 게시판 유형={}", me.id(), req.boardType());
        PostDtos.Create serviceDto = req.toServiceDto();
        Post saved = postService.create(me.id(), serviceDto);
        log.info("게시글 생성 완료: 게시글 ID={}, 제목={}", saved.getId(), saved.getTitle());
        return ResponseEntity.created(URI.create("/api/posts/" + saved.getId()))
                .body(PostRes.of(saved));
    }

    // ====== 목록 계열: 요약만 유지 ======

    /**
     * 게시글 목록 조회 API (요약 정보)
     * 검색어(q)로 필터링 가능
     */
    @GetMapping("/summary")
    public ResponseEntity<Page<PostSummaryRes>> listSummary(
            @Size(max = 100) @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.searchSummary(q, safe).map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    /**
     * 최소 좋아요 수를 기준으로 게시글 목록 조회 API (요약 정보)
     * 검색어(q)와 최소 좋아요 수(minLikes)로 필터링 가능
     */
    @GetMapping("/filter/summary")
    public ResponseEntity<Page<PostSummaryRes>> listWithMinLikesSummary(
            @Size(max = 100) @RequestParam(required = false) String q,
            @PositiveOrZero @RequestParam(defaultValue = "30") long minLikes,
            Pageable pageable
    ) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.searchWithMinLikesSummary(q, minLikes, safe)
                .map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    /**
     * 인기 게시글 목록 조회 API (요약 정보)
     * 조회수가 높은 게시글을 반환
     */
    @GetMapping("/popular/summary")
    public ResponseEntity<Page<PostSummaryRes>> getPopularSummary(Pageable pageable) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.getPopularPostsSummary(safe)
                .map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    /**
     * 최고 게시글 목록 조회 API (요약 정보)
     * 좋아요가 많은 게시글을 반환
     */
    @GetMapping("/best/summary")
    public ResponseEntity<Page<PostSummaryRes>> getBestSummary(Pageable pageable) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.getBestPostsSummary(safe)
                .map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    /**
     * 최근 추천 게시글 목록 조회 API (요약 정보)
     * 최근 일정 기간(days) 내에 좋아요가 많은 게시글을 반환
     */
    @GetMapping("/recommended/summary")
    public ResponseEntity<Page<PostSummaryRes>> getRecentRecommendedSummary(
            @Min(1) @Max(365) @RequestParam(defaultValue = "7") int days,
            Pageable pageable
    ) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.getRecentRecommendedSummary(days, safe)
                .map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    /**
     * 게시판 유형별 게시글 목록 조회 API (요약 정보)
     * 특정 게시판 유형(boardType)의 게시글을 검색어(q)로 필터링하여 반환
     */
    @GetMapping("/board/{boardType}/summary")
    public ResponseEntity<Page<PostSummaryRes>> getByBoardTypeSummary(
            @PathVariable BoardType boardType,
            @Size(max = 100) @RequestParam(required = false) String q,
            Pageable pageable
    ) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        pageable = pageable.getPageSize() > MAX_PAGE_SIZE
            ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
            : pageable;
            
        var safe = PageableUtil.getSafePostPageable(pageable);
        Page<PostSummaryRes> body = postService.searchByBoardTypeSummary(boardType, q, safe)
                .map(PostSummaryRes::of);
        return ResponseEntity.ok(body);
    }

    // ====== 상세/수정/삭제: 엔티티 사용 유지 ======

    /**
     * 게시글 상세 조회 API
     * 조회 시 조회수가 증가됨
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostRes> get(@PathVariable Long id) {
        Post post = postService.getAndIncrementViewCount(id);
        return ResponseEntity.ok(PostRes.of(post));
    }

    /**
     * 게시글 수정 API
     * 게시글 작성자만 수정 가능
     */
    @PreAuthorize("@postSecurity.isOwner(#id, authentication)")
    @PutMapping("/{id}")
    public ResponseEntity<PostRes> update(
            @PathVariable Long id,
            @Valid @RequestBody PostWebDtos.UpdateRequest req,
            @AuthenticationPrincipal MemberDetails me
    ) {
        PostDtos.Update dto = req.toServiceDto();
        Post updated = postService.update(id, dto);
        return ResponseEntity.ok(PostRes.of(updated));
    }

    /**
     * 게시글 삭제 API
     * 관리자 또는 게시글 작성자만 삭제 가능
     */
    @PreAuthorize("hasRole('ADMIN') or @postSecurity.isOwner(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @AuthenticationPrincipal MemberDetails me) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ====== 좋아요 ======

    /**
     * 게시글 좋아요 토글 API
     * 인증된 사용자만 사용 가능
     * 이미 좋아요한 경우 취소, 아닌 경우 좋아요 추가
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        boolean liked = postLikeService.toggleLike(id, me.id());
        long likeCount = postLikeService.getLikeCount(id);
        return ResponseEntity.ok(Map.of("liked", liked, "likeCount", likeCount));
    }

    /**
     * 게시글 좋아요 상태 조회 API
     * 현재 로그인한 사용자의 좋아요 상태와 전체 좋아요 수를 반환
     * 로그인하지 않은 사용자도 사용 가능
     */
    @GetMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> getLikeStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me
    ) {
        long likeCount = postLikeService.getLikeCount(id);
        boolean liked = me != null && postLikeService.isLikedByMember(id, me.id());
        return ResponseEntity.ok(Map.of("liked", liked, "likeCount", likeCount));
    }
}
