package com.example.community.web;

import com.example.community.domain.Comment;
import com.example.community.security.MemberDetails;
import com.example.community.service.CommentService;
import com.example.community.web.dto.CommentRes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/comments")
@Validated // ★ @NotBlank 유효화 작동
public class CommentController {
    private final CommentService commentService;

    public record CreateReq(@NotBlank String content) {}

    @PostMapping
    public ResponseEntity<CommentRes> add(
            @RequestParam Long postId,
            @AuthenticationPrincipal MemberDetails me,
            @RequestBody @Valid CreateReq req
    ) {
        Comment saved = commentService.add(postId, me.id(), req.content());
        return ResponseEntity.created(URI.create("/api/comments/" + saved.getId()))
                .body(CommentRes.of(saved));
    }

    @GetMapping
    public ResponseEntity<List<CommentRes>> getByPost(@RequestParam Long postId) {
        List<CommentRes> comments = commentService.getByPost(postId).stream()
                .map(CommentRes::of)
                .toList();
        return ResponseEntity.ok(comments);
    }

    @PreAuthorize("hasRole('ADMIN') or @commentSecurity.isAuthor(#id, authentication)")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
