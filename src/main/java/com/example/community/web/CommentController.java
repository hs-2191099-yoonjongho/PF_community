package com.example.community.web;

import com.example.community.domain.Comment;
import com.example.community.service.CommentService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/comments")
public class CommentController {
    private final CommentService commentService;

    // 데모: authorId는 파라미터로 전달
    @PostMapping
    public ResponseEntity<Comment> add(@RequestParam Long postId, @RequestParam Long authorId, @RequestParam @NotBlank String content) {
        return ResponseEntity.ok(commentService.add(postId, authorId, content));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
