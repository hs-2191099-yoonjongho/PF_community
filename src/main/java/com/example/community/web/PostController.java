package com.example.community.web;

import com.example.community.domain.Post;
import com.example.community.service.PostService;
import com.example.community.service.dto.PostDtos;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

    // authorId는 데모를 위해 파라미터로 받습니다. (실전에서는 SecurityContext에서 추출)
    @PostMapping
    public ResponseEntity<Post> create(@RequestParam Long authorId, @Valid @RequestBody PostDtos.Create req) {
        return ResponseEntity.ok(postService.create(authorId, req));
    }

    @GetMapping
    public ResponseEntity<Page<Post>> list(@RequestParam(required = false) String q, Pageable pageable) {
        return ResponseEntity.ok(postService.search(q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Post> get(@PathVariable Long id) {
        return ResponseEntity.ok(postService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Post> update(@PathVariable Long id, @Valid @RequestBody PostDtos.Update req) {
        return ResponseEntity.ok(postService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
