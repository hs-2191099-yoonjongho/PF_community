package com.example.community.web;

import com.example.community.domain.Post;
import com.example.community.security.MemberDetails;
import com.example.community.service.PostService;
import com.example.community.service.dto.PostDtos;
import com.example.community.web.dto.PostRes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/posts")
public class PostController {
    private final PostService postService;

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

    @GetMapping("/{id}")
    public ResponseEntity<PostRes> get(@PathVariable Long id) {
        Post p = postService.get(id);
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
}
