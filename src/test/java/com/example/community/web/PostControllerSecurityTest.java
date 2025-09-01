package com.example.community.web;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;
import com.example.community.repository.PostRepository;
import com.example.community.security.PostSecurity;
import com.example.community.service.PostService;
import com.example.community.service.PostLikeService;
import com.example.community.service.dto.PostDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.community.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게시글 컨트롤러의 보안 관련 테스트
 * Spring Security를 통합하여 인증/인가 기능 테스트
 */
@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
@org.springframework.test.context.TestPropertySource(properties = {"ALLOWED_ORIGINS=http://localhost:3000"})
class PostControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostService postService;

        @MockBean(name = "postSecurity")
        private PostSecurity postSecurity;
    
    // 추가 필요한 모의 빈들 (컨트롤러 의존성에 따라 추가)
    @MockBean
    private PostRepository postRepository;

        @MockBean
        private PostLikeService postLikeService;

                // SecurityConfig dependencies
                @org.springframework.boot.test.mock.mockito.MockBean
                private com.example.community.config.JwtUtil jwtUtil;
                @org.springframework.boot.test.mock.mockito.MockBean
                private com.example.community.service.CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("익명 사용자는 게시글 목록을 조회할 수 있음")
    @WithAnonymousUser
    void anonymousUserCanAccessPostList() throws Exception {
        // given
        Page<Post> emptyPage = new PageImpl<>(new ArrayList<>());
        when(postService.search(any(), any())).thenReturn(emptyPage);

        // when & then
        mockMvc.perform(get("/api/posts").header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("익명 사용자는 게시글을 작성할 수 없음")
    @WithAnonymousUser
    void anonymousUserCannotCreatePost() throws Exception {
        // given
        PostDtos.Create createDto = new PostDtos.Create(
                "테스트 제목",
                "테스트 내용",
                BoardType.FREE,
                null
        );

        // when & then
        mockMvc.perform(post("/api/posts")
                .with(csrf())
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isUnauthorized());

        // 서비스 메서드가 호출되지 않았는지 확인
        verify(postService, never()).create(anyLong(), any(PostDtos.Create.class));
    }

    @Test
    @DisplayName("인증된 사용자는 게시글을 작성할 수 있음")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void authenticatedUserCanCreatePost() throws Exception {
        // given
        PostDtos.Create createDto = new PostDtos.Create(
                "테스트 제목",
                "테스트 내용",
                BoardType.FREE,
                null
        );

        com.example.community.domain.Member author = com.example.community.domain.Member.builder()
                .id(1L).email("testuser@example.com").username("testuser").password("pw").build();
        Post createdPost = Post.builder()
                .id(1L)
                .title("테스트 제목")
                .content("테스트 내용")
                .boardType(BoardType.FREE)
                .author(author)
                .build();

        when(postService.create(anyLong(), any(PostDtos.Create.class))).thenReturn(createdPost);
        when(postSecurity.isNoticeAllowed(any(PostDtos.Create.class))).thenReturn(true);

        // when & then
        mockMvc.perform(post("/api/posts")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.example.community.security.MemberDetails(1L, "testuser", "pw", java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ADMIN 권한이 없는 사용자는 공지사항을 작성할 수 없음")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void nonAdminUserCannotCreateNotice() throws Exception {
        // given
        PostDtos.Create noticeDto = new PostDtos.Create(
                "공지사항 제목",
                "공지사항 내용",
                BoardType.NOTICE,
                null
        );

        // PostSecurity 모킹 - 일반 사용자는 공지사항 작성 불가
        when(postSecurity.isNoticeAllowed(any(PostDtos.Create.class))).thenReturn(false);

        // when & then
        mockMvc.perform(post("/api/posts")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.example.community.security.MemberDetails(1L, "testuser", "pw", java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noticeDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN 사용자는 공지사항을 작성할 수 있음")
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminUserCanCreateNotice() throws Exception {
        // given
        PostDtos.Create noticeDto = new PostDtos.Create(
                "공지사항 제목",
                "공지사항 내용",
                BoardType.NOTICE,
                null
        );

        com.example.community.domain.Member admin = com.example.community.domain.Member.builder()
                .id(99L).email("admin@example.com").username("admin").password("pw")
                .roles(java.util.Set.of("ROLE_USER", "ROLE_ADMIN"))
                .build();

        Post created = Post.builder()
                .id(10L)
                .title("공지사항 제목")
                .content("공지사항 내용")
                .boardType(BoardType.NOTICE)
                .author(admin)
                .build();

        when(postService.create(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(PostDtos.Create.class)))
                .thenReturn(created);

        // when & then
        mockMvc.perform(post("/api/posts")
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.example.community.security.MemberDetails(99L, "admin", "pw", java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"), new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(noticeDto)))
                .andExpect(status().isCreated());

        verify(postService).create(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(PostDtos.Create.class));
    }

    @Test
    @DisplayName("게시글 소유자만 게시글을 삭제할 수 있음")
    @WithMockUser(username = "testuser", roles = {"USER"})
    void onlyOwnerCanDeletePost() throws Exception {
        // given
        Long postId = 1L;

        // 소유자가 아닌 경우
        when(postSecurity.isOwner(eq(postId), any())).thenReturn(false);

        // when & then
        when(postSecurity.isOwner(eq(postId), any())).thenReturn(false);
        mockMvc.perform(delete("/api/posts/{id}", postId)
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.example.community.security.MemberDetails(1L, "testuser", "pw", java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isForbidden());

        // 서비스 메서드가 호출되지 않았는지 확인
        verify(postService, never()).delete(postId);
    }

    @Test
    @DisplayName("관리자는 모든 게시글을 삭제할 수 있음")
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void adminCanDeleteAnyPost() throws Exception {
        // given
        Long postId = 1L;

        // 관리자는 항상 삭제 가능
        when(postSecurity.isOwner(eq(postId), any())).thenReturn(false); // 소유자가 아니더라도

        // when & then
        mockMvc.perform(delete("/api/posts/{id}", postId)
                .with(csrf())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(new com.example.community.security.MemberDetails(99L, "admin", "pw", java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"), new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNoContent());

        // 서비스 메서드가 호출되었는지 확인
        verify(postService).delete(postId);
    }
}
