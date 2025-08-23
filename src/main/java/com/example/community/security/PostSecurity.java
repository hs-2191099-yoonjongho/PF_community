package com.example.community.security;

import com.example.community.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostSecurity {
    private final PostRepository posts;

    public boolean isOwner(Long postId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        String username = authentication.getName();
        return posts.existsByIdAndAuthor_Username(postId, username);
    }
}