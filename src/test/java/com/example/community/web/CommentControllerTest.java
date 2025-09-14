package com.example.community.web;

import com.example.community.auth.Actor;
import com.example.community.auth.ActorMapper;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.repository.dto.CommentProjection;
import com.example.community.security.MemberDetails;
import com.example.community.service.CommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = true)
@TestPropertySource(properties = { "ALLOWED_ORIGINS=http://localhost:3000" })
@Import(CommentControllerTest.MethodSec.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CommentService commentService;

    // 보안 설정을 위한 추가 Mock 빈들

    // 테스트 데이터 설정을 위한 헬퍼 메서드
    private Member createTestMember() {
        return Member.builder()
                .id(1L)
                .username("테스터")
                .email("test@example.com")
                .roles(Set.of("ROLE_USER"))
                .build();
    }

    // helper removed: createTestPost was unused

    @Test
    @WithMockUser
    @DisplayName("게시글 ID로 페이징된 댓글 조회")
    void getByPostPaged() throws Exception {
        // given
        Long postId = 1L;
        var member1 = new CommentProjection.MemberDto(1L, "테스터");

        List<CommentProjection> commentProjections = List.of(
                new CommentProjection(1L, "첫 번째 댓글", LocalDateTime.now(), member1, postId),
                new CommentProjection(2L, "두 번째 댓글", LocalDateTime.now(), member1, postId));

        Pageable pageable = PageRequest.of(0, 20);
        Page<CommentProjection> projectionPage = new PageImpl<>(commentProjections, pageable,
                commentProjections.size());

        when(commentService.getProjectionsByPostWithPaging(eq(postId), any(Pageable.class)))
                .thenReturn(projectionPage);

        // when & then
        mockMvc.perform(get("/api/posts/{postId}/comments", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].content").value("첫 번째 댓글"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1))
                .andExpect(jsonPath("$.pageInfo.first").value(true))
                .andExpect(jsonPath("$.pageInfo.last").value(true));
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "USER" })
    @DisplayName("인증된 사용자는 댓글을 작성할 수 있음")
    void createCommentAuthenticated() throws Exception {
        // given
        Long postId = 1L;
        String content = "테스트 댓글입니다";
        CommentController.CreateReq createReq = new CommentController.CreateReq(content);
        Member author = createTestMember();
        var post = com.example.community.domain.Post.builder().id(1L).title("t").content("c").author(author).build();
        Comment savedComment = Comment.builder()
                .id(1L)
                .content(content)
                .post(post)
                .author(author)
                .build();
        // authorId = 1L (인증 주체)
        when(commentService.add(eq(postId), eq(1L), eq(content)))
                .thenReturn(savedComment);
        // when & then
        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                .with(csrf())
                .with(user(new MemberDetails(1L, "test@example.com", "pw",
                        java.util.Set.of(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")))))
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.comment.id").value(1))
                .andExpect(jsonPath("$.comment.content").value(content))
                .andExpect(jsonPath("$.comment.author.username").value(author.getUsername()));
        // 서비스 메서드 호출 확인
        verify(commentService).add(eq(postId), eq(1L), eq(content));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSec {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain testSecurityFilterChain(
                org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Test
    @WithAnonymousUser
    @DisplayName("인증되지 않은 사용자는 댓글을 작성할 수 없음")
    void createCommentUnauthenticated() throws Exception {
        // given
        Long postId = 1L;
        String content = "테스트 댓글입니다";
        CommentController.CreateReq createReq = new CommentController.CreateReq(content);
        // when & then
        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                .with(csrf())
                .header("Origin", "http://localhost:3000")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isForbidden()); // 403으로 변경
        // 서비스 메서드 호출되지 않음 확인
        verify(commentService, never()).add(anyLong(), anyLong(), anyString());
    }

    @Test
    @WithMockUser(username = "test@example.com", roles = { "USER" })
    @DisplayName("댓글 작성자는 자신의 댓글을 삭제할 수 있음")
    void deleteCommentByAuthor() throws Exception {
        // given
        Long commentId = 1L;
        Long memberId = 1L;
        MemberDetails memberDetails = new MemberDetails(memberId, "test@example.com", "pw",
                java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        Actor actor = ActorMapper.from(memberDetails);
        doNothing().when(commentService).delete(commentId, actor);
        // when & then
        mockMvc.perform(delete("/api/comments/{id}", commentId)
                .with(csrf())
                .with(user(memberDetails))
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
        // 서비스 메서드 호출 확인
        verify(commentService).delete(commentId, actor);
    }

    @Test
    @WithMockUser(username = "other@example.com", roles = { "USER" })
    @DisplayName("다른 사용자는 작성자의 댓글을 삭제할 수 없음")
    void deleteCommentByNonAuthor() throws Exception {
        // given
        Long commentId = 1L;
        Long memberId = 2L;
        MemberDetails memberDetails = new MemberDetails(memberId, "other@example.com", "pw",
                java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        Actor actor = ActorMapper.from(memberDetails);
        doThrow(new com.example.community.service.exception.EntityNotFoundException("삭제할 댓글(권한 없음)", commentId))
                .when(commentService).delete(commentId, actor);
        // when & then
        mockMvc.perform(delete("/api/comments/{id}", commentId)
                .with(csrf())
                .with(user(memberDetails))
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isNotFound());
        // 서비스 메서드 호출 확인
        verify(commentService).delete(commentId, actor);
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = { "USER", "ADMIN" })
    @DisplayName("관리자는 모든 댓글을 삭제할 수 있음")
    void deleteCommentByAdmin() throws Exception {
        // given
        Long commentId = 1L;
        Long memberId = 99L;
        MemberDetails memberDetails = new MemberDetails(memberId, "admin@example.com", "pw",
                java.util.Set.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")));
        Actor actor = ActorMapper.from(memberDetails);
        doNothing().when(commentService).delete(commentId, actor);
        // when & then
        mockMvc.perform(delete("/api/comments/{id}", commentId)
                .with(csrf())
                .with(user(memberDetails))
                .header("Origin", "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
        // 서비스 메서드 호출 확인
        verify(commentService).delete(commentId, actor);
    }
}
