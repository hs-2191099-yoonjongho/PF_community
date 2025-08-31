package com.example.community.security;

import com.example.community.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 보안 컴포넌트의 단위 테스트
 * 실제 Spring Security 없이 권한 검증 로직을 테스트
 */
@ExtendWith(MockitoExtension.class)
class PostSecurityTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostSecurity postSecurity;

    private Authentication anonymousAuth;
    private Authentication ownerAuth;
    private Authentication nonOwnerAuth;

    @BeforeEach
    void setUp() {
        // 익명 사용자 Authentication 설정
        anonymousAuth = new AnonymousAuthenticationToken(
                "anonymous", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );

        // 게시글 소유자 Authentication 설정
        MemberDetails ownerDetails = new MemberDetails(
                3L, "owner", null, 
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        ownerAuth = new TestingAuthenticationToken(
                ownerDetails, null, 
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        // 게시글 비소유자 Authentication 설정
        MemberDetails nonOwnerDetails = new MemberDetails(
                4L, "nonowner", null, 
                Set.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        nonOwnerAuth = new TestingAuthenticationToken(
                nonOwnerDetails, null, 
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    @DisplayName("익명 사용자는 게시글 소유자 검증에 실패해야 함")
    void anonymousUserIsNotOwner() {
        // when
        boolean result = postSecurity.isOwner(1L, anonymousAuth);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("게시글 소유자 검증 - 소유자인 경우 true 반환")
    void isOwnerReturnsTrueForPostOwner() {
        // given
        Long postId = 1L;
        MemberDetails memberDetails = (MemberDetails) ownerAuth.getPrincipal();

        when(postRepository.existsByIdAndAuthor_Id(eq(postId), eq(memberDetails.id())))
                .thenReturn(true);

        // when
        boolean result = postSecurity.isOwner(postId, ownerAuth);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("게시글 소유자 검증 - 소유자가 아닌 경우 false 반환")
    void isOwnerReturnsFalseForNonOwner() {
        // given
        Long postId = 1L;
        MemberDetails memberDetails = (MemberDetails) nonOwnerAuth.getPrincipal();

        when(postRepository.existsByIdAndAuthor_Id(eq(postId), eq(memberDetails.id())))
                .thenReturn(false);

        // when
        boolean result = postSecurity.isOwner(postId, nonOwnerAuth);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("인증 객체가 null인 경우 소유자 검증에 실패해야 함")
    void isOwnerReturnsFalseForNullAuthentication() {
        // when
        boolean result = postSecurity.isOwner(1L, null);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("일반 사용자는 공지사항 작성 권한이 없어야 함")
    void regularUserCannotCreateNotice() {
        // given
        com.example.community.service.dto.PostDtos.Create noticeDto = 
                new com.example.community.service.dto.PostDtos.Create(
                "공지사항 제목", 
                "공지사항 내용", 
                com.example.community.domain.BoardType.NOTICE, 
                null
        );

        // when
        boolean result = postSecurity.isNoticeAllowed(noticeDto);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("일반 사용자는 자유게시판 작성 권한이 있어야 함")
    void regularUserCanCreateFreePost() {
        // given
        com.example.community.service.dto.PostDtos.Create freeDto = 
                new com.example.community.service.dto.PostDtos.Create(
                "자유게시판 제목", 
                "자유게시판 내용", 
                com.example.community.domain.BoardType.FREE, 
                null
        );

        // when
        boolean result = postSecurity.isNoticeAllowed(freeDto);

        // then
        assertThat(result).isTrue();
    }
}
