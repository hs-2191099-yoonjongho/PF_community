package com.example.community.security;

import com.example.community.domain.BoardType;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("postSecurity")
@RequiredArgsConstructor
public class PostSecurity {
    private final PostRepository posts;

    public boolean isOwner(Long postId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) return false;

        Object p = authentication.getPrincipal();
        if (!(p instanceof MemberDetails md)) return false;

        return posts.existsByIdAndAuthor_Id(postId, md.id()); // ★ id 기준
    }
    
    /**
     * 공지사항 작성 권한 확인
     * NOTICE 게시판 타입은 관리자만 작성 가능하므로,
     * 일반 사용자는 NOTICE가 아닌 경우에만 게시글 작성 가능
     */
    public boolean isNoticeAllowed(PostDtos.Create req) {
        if (req == null || req.boardType() == null) {
            return true;
        }
        
        // 일반 사용자는 NOTICE가 아닌 경우에만 게시글 작성 가능
        return req.boardType() != BoardType.NOTICE;
    }
}