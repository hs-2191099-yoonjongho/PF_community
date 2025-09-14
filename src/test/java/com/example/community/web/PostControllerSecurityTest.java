package com.example.community.web;

import com.example.community.auth.Actor;
import com.example.community.service.exception.ForbiddenOperationException;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;
import com.example.community.service.PostService;
import com.example.community.service.dto.PostDtos;
import com.example.community.web.dto.PostWebDtos;
import com.example.community.service.dto.PostSummaryDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
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
@org.springframework.test.context.TestPropertySource(properties = { "ALLOWED_ORIGINS=http://localhost:3000" })
class PostControllerSecurityTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private PostService postService;

        @Test
        @DisplayName("익명 사용자는 게시글 목록을 조회할 수 있음")
        @WithAnonymousUser
        void anonymousUserCanAccessPostList() throws Exception {
                // given
                Page<PostSummaryDto> emptyPage = new PageImpl<>(new ArrayList<>());
                when(postService.searchSummary(any(), any())).thenReturn(emptyPage);

                // when & then
                mockMvc.perform(get("/api/posts/summary").header("Origin", "http://localhost:3000"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("익명 사용자는 게시글을 작성할 수 없음")
        @WithAnonymousUser
        void anonymousUserCannotCreatePost() throws Exception {
                // given
                PostWebDtos.CreateRequest createDto = new PostWebDtos.CreateRequest(
                                "테스트 제목",
                                "테스트 내용",
                                BoardType.FREE,
                                null);

                // when & then
                mockMvc.perform(post("/api/posts")
                                .with(csrf())
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDto)))
                                .andExpect(status().isUnauthorized());

                // 서비스 메서드가 호출되지 않았는지 확인
                verify(postService, never()).create(any(Actor.class), any(PostDtos.Create.class));
        }

        @Test
        @DisplayName("인증된 사용자는 게시글을 작성할 수 있음")
        @WithMockUser(username = "testuser", roles = { "USER" })
        void authenticatedUserCanCreatePost() throws Exception {
                // given
                PostWebDtos.CreateRequest createDto = new PostWebDtos.CreateRequest(
                                "테스트 제목",
                                "테스트 내용",
                                BoardType.FREE,
                                null);

                com.example.community.domain.Member author = com.example.community.domain.Member.builder()
                                .id(1L).email("testuser@example.com").username("testuser").password("pw").build();
                Post createdPost = Post.builder()
                                .id(1L)
                                .title("테스트 제목")
                                .content("테스트 내용")
                                .boardType(BoardType.FREE)
                                .author(author)
                                .build();

                when(postService.create(any(Actor.class), any(PostDtos.Create.class))).thenReturn(createdPost);

                // when & then
                mockMvc.perform(post("/api/posts")
                                .with(csrf())
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user(new com.example.community.security.MemberDetails(1L, "testuser",
                                                                "pw",
                                                                java.util.Set.of(
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_USER")))))
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createDto)))
                                .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("ADMIN 권한이 없는 사용자는 공지사항을 작성할 수 없음")
        @WithMockUser(username = "testuser", roles = { "USER" })
        void nonAdminUserCannotCreateNotice() throws Exception {
                // given
                PostWebDtos.CreateRequest noticeDto = new PostWebDtos.CreateRequest(
                                "공지사항 제목",
                                "공지사항 내용",
                                BoardType.NOTICE,
                                null);

                // 권한 없는 사용자는 서비스에서 예외 발생
                when(postService.create(any(Actor.class), any(PostDtos.Create.class)))
                                .thenThrow(new ForbiddenOperationException("공지사항 작성 권한 없음"));

                // when & then
                mockMvc.perform(post("/api/posts")
                                .with(csrf())
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user(new com.example.community.security.MemberDetails(1L, "testuser",
                                                                "pw",
                                                                java.util.Set.of(
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_USER")))))
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noticeDto)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ADMIN 사용자는 공지사항을 작성할 수 있음")
        @WithMockUser(username = "admin", roles = { "USER", "ADMIN" })
        void adminUserCanCreateNotice() throws Exception {
                // given
                PostWebDtos.CreateRequest noticeDto = new PostWebDtos.CreateRequest(
                                "공지사항 제목",
                                "공지사항 내용",
                                BoardType.NOTICE,
                                null);

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

                when(postService.create(org.mockito.ArgumentMatchers.any(Actor.class),
                                org.mockito.ArgumentMatchers.any(PostDtos.Create.class)))
                                .thenReturn(created);

                // when & then
                mockMvc.perform(post("/api/posts")
                                .with(csrf())
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user(new com.example.community.security.MemberDetails(99L, "admin",
                                                                "pw",
                                                                java.util.Set.of(
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_ADMIN"),
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_USER")))))
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(noticeDto)))
                                .andExpect(status().isCreated());

                verify(postService).create(org.mockito.ArgumentMatchers.any(Actor.class),
                                org.mockito.ArgumentMatchers.any(PostDtos.Create.class));
        }

        @Test
        @DisplayName("게시글 소유자만 게시글을 삭제할 수 있음")
        @WithMockUser(username = "testuser", roles = { "USER" })
        void onlyOwnerCanDeletePost() throws Exception {
                // given
                Long postId = 1L;

                // 소유자가 아닌 경우: delete 호출 시 ForbiddenOperationException 발생하도록 mock
                org.mockito.Mockito.doThrow(new ForbiddenOperationException("삭제 권한이 없습니다."))
                                .when(postService).delete(eq(postId), any(Actor.class));

                // when & then
                mockMvc.perform(delete("/api/posts/{id}", postId)
                                .with(csrf())
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user(new com.example.community.security.MemberDetails(1L, "testuser",
                                                                "pw",
                                                                java.util.Set.of(
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_USER")))))
                                .header("Origin", "http://localhost:3000"))
                                .andExpect(status().isForbidden());

                // 서비스 메서드가 호출되었는지 확인
                verify(postService).delete(eq(postId), any(Actor.class));
        }

        @Test
        @DisplayName("관리자는 모든 게시글을 삭제할 수 있음")
        @WithMockUser(username = "admin", roles = { "USER", "ADMIN" })
        void adminCanDeleteAnyPost() throws Exception {
                // given
                Long postId = 1L;

                // 관리자는 항상 삭제 가능

                // when & then
                mockMvc.perform(delete("/api/posts/{id}", postId)
                                .with(csrf())
                                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                                .user(new com.example.community.security.MemberDetails(99L, "admin",
                                                                "pw",
                                                                java.util.Set.of(
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_ADMIN"),
                                                                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                                                                                "ROLE_USER")))))
                                .header("Origin", "http://localhost:3000"))
                                .andExpect(status().isNoContent());

                // 서비스 메서드가 호출되었는지 확인
                verify(postService).delete(eq(postId), any(Actor.class));
        }
}
