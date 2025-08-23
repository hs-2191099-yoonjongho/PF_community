package com.example.community.security;

import com.example.community.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("commentSecurity")
@RequiredArgsConstructor
public class CommentSecurity {
    private final CommentRepository comments;

    public boolean isAuthor(Long commentId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) return false;

        Object p = authentication.getPrincipal();
        if (!(p instanceof MemberDetails md)) return false;

        return comments.findAuthorIdById(commentId)
                .map(md.id()::equals)
                .orElse(false);
    }
}
