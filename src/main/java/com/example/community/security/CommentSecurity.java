package com.example.community.security;

import com.example.community.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;


 // 댓글 관련 보안 검증을 처리하는 컴포넌트

@Component("commentSecurity")
@RequiredArgsConstructor
public class CommentSecurity {
    private final CommentRepository comments;

    /**
     * 현재 인증된 사용자가 특정 댓글의 작성자인지 확인합니다.
     * 
     * @param commentId 확인할 댓글의 ID
     * @param authentication 현재 인증 정보 (Spring Security Context에서 제공)
     * @return 현재 인증된 사용자가 해당 댓글의 작성자인 경우 true, 그렇지 않으면 false
     */
    public boolean isAuthor(Long commentId, Authentication authentication) {
        // 인증되지 않았거나 익명 사용자인 경우 즉시 false 반환
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) return false;

        // Principal에서 사용자 정보 추출
        Object p = authentication.getPrincipal();
        if (!(p instanceof MemberDetails md)) return false;

        // 댓글 작성자 ID 조회 후 현재 사용자와 비교
        return comments.findAuthorIdById(commentId)
                .map(md.id()::equals)  // 작성자 ID와 현재 사용자 ID 비교
                .orElse(false);        // 댓글이 존재하지 않으면 false 반환
    }
}
