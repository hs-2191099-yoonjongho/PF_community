package com.example.community.web;

import com.example.community.domain.Comment;
import com.example.community.repository.dto.CommentProjection;
import com.example.community.security.MemberDetails;
import com.example.community.auth.Actor;
import com.example.community.auth.ActorMapper;
import com.example.community.service.CommentService;
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
import java.util.Map;

@RequiredArgsConstructor
@RestController
@Validated
public class CommentController {
    private final CommentService commentService;

    public record CreateReq(
            @NotBlank @Size(max = 1000) String content) {
    }

    /**
     * 댓글 작성
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<?> add(
            @PathVariable Long postId,
            @AuthenticationPrincipal MemberDetails me,
            @RequestBody @Valid CreateReq req) {
        long userId = me.getId();
        Comment saved = commentService.add(postId, userId, req.content());
        return ResponseEntity.created(URI.create("/api/comments/" + saved.getId()))
                .body(Map.of(
                        "success", true,
                        "comment", CommentRes.of(saved)));
    }

    /**
     * 게시글에 달린 댓글을 최적화된 방식으로 페이징하여 조회
     * - N+1 쿼리 문제가 해결된 API
     * 
     * @param postId   게시글 ID
     * @param pageable 페이징 정보 (page, size, sort)
     * @return 페이징된 댓글 목록과 페이지 정보
     */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<?> getCommentsByPost(
            @PathVariable Long postId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        // 페이지 크기 상한 제한 (최대 50개)
        final int MAX_PAGE_SIZE = 50;
        Pageable cappedPageable = pageable.getPageSize() > MAX_PAGE_SIZE
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE,
                        pageable.getSort())
                : pageable;

        Page<CommentProjection> projectionPage = commentService.getProjectionsByPostWithPaging(postId, cappedPageable);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "content", projectionPage.getContent().stream().map(CommentRes::from).toList(),
                "pageInfo", Map.of(
                        "page", projectionPage.getNumber(),
                        "size", projectionPage.getSize(),
                        "totalElements", projectionPage.getTotalElements(),
                        "totalPages", projectionPage.getTotalPages(),
                        "first", projectionPage.isFirst(),
                        "last", projectionPage.isLast())));
    }

    /**
     * 댓글 삭제
     */
    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id,
            @AuthenticationPrincipal MemberDetails me) {
        // MemberDetails -> Actor 변환 (매퍼 사용)
        Actor actor = ActorMapper.from(me);
        commentService.delete(id, actor);
        return ResponseEntity.ok(Map.of("success", true, "message", "댓글이 성공적으로 삭제되었습니다"));
    }
}
