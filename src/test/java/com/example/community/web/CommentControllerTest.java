package com.example.community.web;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.service.CommentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentController.class)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;
    
    // 보안 설정을 위한 추가 Mock 빈들
    @MockBean
    private com.example.community.security.CommentSecurity commentSecurity;

    @Test
    @WithMockUser
    @DisplayName("게시글 ID로 페이징된 댓글 조회")
    void getByPostPaged() throws Exception {
        // given
        Long postId = 1L;
        Member author = Member.builder()
                .id(1L)
                .username("테스터")
                .email("test@example.com")
                .roles(Set.of("ROLE_USER"))
                .build();

        Post post = Post.builder()
                .id(postId)
                .title("테스트 게시글")
                .content("테스트 내용입니다.")
                .author(author)
                .build();

        LocalDateTime now = LocalDateTime.now();
        Comment comment1 = Comment.builder()
                .id(1L)
                .post(post)
                .author(author)
                .content("첫 번째 댓글")
                .build();
        
        // Comment 엔티티에 createdAt 필드 설정 방법이 없다면 리플렉션 사용 (테스트 코드만을 위한 방법)
        try {
            java.lang.reflect.Field createdAtField = comment1.getClass().getSuperclass().getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(comment1, now);
        } catch (Exception e) {
            // 리플렉션 실패 시 무시 (테스트에 중요하지 않음)
        }

        Comment comment2 = Comment.builder()
                .id(2L)
                .post(post)
                .author(author)
                .content("두 번째 댓글")
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        List<Comment> comments = List.of(comment1, comment2);
        Page<Comment> commentPage = new PageImpl<>(comments, pageable, comments.size());

        when(commentService.getByPostWithPaging(eq(postId), any(Pageable.class)))
                .thenReturn(commentPage);

        // when & then
        mockMvc.perform(get("/api/comments/paged")
                .param("postId", postId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].content").value("첫 번째 댓글"))
                .andExpect(jsonPath("$.pageInfo.totalElements").value(2))
                .andExpect(jsonPath("$.pageInfo.totalPages").value(1))
                .andExpect(jsonPath("$.pageInfo.first").value(true))
                .andExpect(jsonPath("$.pageInfo.last").value(true));
    }
}
