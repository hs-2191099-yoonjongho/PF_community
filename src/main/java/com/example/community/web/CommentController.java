package com.example.community.web;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;
import com.example.community.security.MemberDetails;
import com.example.community.service.CommentService;
import com.example.community.util.PageableUtil;
import com.example.community.web.dto.CommentRes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Validated
public class CommentController {
    private final CommentService commentService;

    public record CreateReq(
            @NotBlank 
            @Size(max = 1000)
            String content
    ) {}

    /**
     * 댓글 작성
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<CommentRes> add(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails me,
            @RequestBody @Valid CreateReq req
    ) {
        Comment saved = commentService.add(postId, me.id(), req.content());
        return ResponseEntity.created(URI.create("/api/comments/" + saved.getId()))
                .body(CommentRes.of(saved));
    }

    /**
     * @deprecated 최적화되지 않은 API입니다. 대신 {@link #getCommentsByPost(Long, Pageable)}를 사용하세요.
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/api/comments")
    public ResponseEntity<List<CommentRes>> getByPost(@RequestParam Long postId) {
        List<CommentRes> comments = commentService.getByPost(postId).stream()
                .map(CommentRes::of)
                .toList();
        return ResponseEntity.ok(comments);
    }
    
    /**
     * @deprecated 최적화되지 않은 API입니다. 대신 {@link #getCommentsByPost(Long, Pageable)}를 사용하세요.
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/api/comments/paged")
    public ResponseEntity<Map<String, Object>> getByPostPaged(
            @RequestParam Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafeCommentPageable(pageable);
        
        Page<Comment> commentPage = commentService.getByPostWithPaging(postId, safePageable);
        
        Map<String, Object> response = Map.of(
            "content", commentPage.getContent().stream().map(CommentRes::of).toList(),
            "pageInfo", Map.of(
                "page", commentPage.getNumber(),
                "size", commentPage.getSize(),
                "totalElements", commentPage.getTotalElements(),
                "totalPages", commentPage.getTotalPages(),
                "first", commentPage.isFirst(),
                "last", commentPage.isLast()
            )
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * @deprecated 최적화되지 않은 API입니다. 대신 {@link #getCommentsByPost(Long, Pageable)}를 사용하세요.
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/api/comments/optimized")
    public ResponseEntity<Map<String, Object>> getOptimizedByPostPaged(
            @RequestParam Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafeCommentPageable(pageable);
        
        Page<CommentProjection> projectionPage = 
                commentService.getProjectionsByPostWithPaging(postId, safePageable);
        
        Map<String, Object> response = Map.of(
            "content", projectionPage.getContent().stream().map(CommentRes::from).toList(),
            "pageInfo", Map.of(
                "page", projectionPage.getNumber(),
                "size", projectionPage.getSize(),
                "totalElements", projectionPage.getTotalElements(),
                "totalPages", projectionPage.getTotalPages(),
                "first", projectionPage.isFirst(),
                "last", projectionPage.isLast()
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 게시글에 달린 댓글을 최적화된 방식으로 페이징하여 조회
     * - N+1 쿼리 문제가 해결된 API
     * 
     * @param postId 게시글 ID
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 페이징된 댓글 목록과 페이지 정보
     */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> getCommentsByPost(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        // 안전한 정렬 적용
        Pageable safePageable = PageableUtil.getSafeCommentPageable(pageable);
        
        Page<CommentProjection> projectionPage = 
                commentService.getProjectionsByPostWithPaging(postId, safePageable);
        
        Map<String, Object> response = Map.of(
            "content", projectionPage.getContent().stream().map(CommentRes::from).toList(),
            "pageInfo", Map.of(
                "page", projectionPage.getNumber(),
                "size", projectionPage.getSize(),
                "totalElements", projectionPage.getTotalElements(),
                "totalPages", projectionPage.getTotalPages(),
                "first", projectionPage.isFirst(),
                "last", projectionPage.isLast()
            )
        );
        
        return ResponseEntity.ok(response);
    }

    /**
     * 댓글 삭제
     */
    @PreAuthorize("hasRole('ADMIN') or @commentSecurity.isAuthor(#id, authentication)")
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
