package com.example.community.security;

import com.example.community.domain.BoardType;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;


 // 게시글 관련 보안 검증을 처리하는 컴포넌트
@Component("postSecurity")
@RequiredArgsConstructor
public class PostSecurity {
    private final PostRepository posts;

    /**
     * 현재 인증된 사용자가 특정 게시글의 작성자인지 확인합니다.
     * @param postId 확인할 게시글의 ID
     * @param authentication 현재 인증 정보 (Spring Security Context에서 제공)
     * @return 현재 인증된 사용자가 해당 게시글의 작성자인 경우 true, 그렇지 않으면 false
     */
    public boolean isOwner(Long postId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) return false;

        Object p = authentication.getPrincipal();
        if (!(p instanceof MemberDetails md)) return false;

        return posts.existsByIdAndAuthor_Id(postId, md.id()); // ★ id 기준
    }
    
    /**
     * 게시판 유형이 공지사항인지 확인
     * 일반 사용자는 NOTICE 게시판에 글을 작성할 수 없음
     * 
     * @param boardType 확인할 게시판 유형
     * @return 공지사항이 아닌 경우에만 true, 공지사항이거나 유효하지 않은 경우 false
     */
    public boolean isBoardTypeAllowed(BoardType boardType) {
        if (boardType == null) {
            return false;
        }
        
        // 일반 사용자는 NOTICE가 아닌 경우에만 게시글 작성 가능
        return boardType != BoardType.NOTICE;
    }
    
    /**
     * 공지사항 작성 권한 확인
     * 일반 사용자는 NOTICE가 아닌 경우에만 게시글 작성 가능합니다.</p>
     * @param req 게시글 생성 요청 데이터 객체
     * @return 공지사항이 아닌 경우에만 true, 공지사항이거나 요청이 유효하지 않은 경우 false
     * @deprecated {@link #isBoardTypeAllowed(BoardType)}를 사용하세요.
     */
    @Deprecated
    public boolean isNoticeAllowed(PostDtos.Create req) {
        if (req == null || req.boardType() == null) {
            return false;
        }
        
        return isBoardTypeAllowed(req.boardType());
    }
}