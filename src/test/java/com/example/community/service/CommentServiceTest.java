package com.example.community.service;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.CommentRepository;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.example.community.repository.dto.CommentProjection;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

        @Mock
        private CommentRepository commentRepository;

        @Mock
        private PostRepository postRepository;

        @Mock
        private MemberRepository memberRepository;

        @InjectMocks
        private CommentService commentService;

        private Member author;
        private Post post;
        private Comment comment1, comment2;

        @BeforeEach
        void setUp() {
                // 테스트 데이터 설정
                author = Member.builder()
                                .id(1L)
                                .username("테스터")
                                .email("test@example.com")
                                .password("password")
                                .roles(Set.of("ROLE_USER"))
                                .build();

                post = Post.builder()
                                .id(1L)
                                .title("테스트 게시글")
                                .content("테스트 내용입니다.")
                                .author(author)
                                .viewCount(0)
                                .build();

                comment1 = Comment.builder()
                                .id(1L)
                                .post(post)
                                .author(author)
                                .content("첫 번째 댓글")
                                .build();

                comment2 = Comment.builder()
                                .id(2L)
                                .post(post)
                                .author(author)
                                .content("두 번째 댓글")
                                .build();
        }

        @Test
        @DisplayName("게시글 ID로 댓글 프로젝션 페이징 조회")
        void getProjectionsByPostWithPaging() {
                // given
                Pageable pageable = PageRequest.of(0, 10);
                when(postRepository.existsById(post.getId())).thenReturn(true);

                CommentProjection.MemberDto authorDto = new CommentProjection.MemberDto(author.getId(),
                                author.getUsername());
                CommentProjection p1 = new CommentProjection(comment1.getId(), comment1.getContent(),
                                comment1.getCreatedAt(), authorDto, post.getId());
                CommentProjection p2 = new CommentProjection(comment2.getId(), comment2.getContent(),
                                comment2.getCreatedAt(), authorDto, post.getId());
                Page<CommentProjection> projectionPage = new PageImpl<>(List.of(p1, p2), pageable, 2);

                when(commentRepository.findProjectionsByPostId(eq(post.getId()), any(Pageable.class)))
                                .thenReturn(projectionPage);

                // when
                Page<CommentProjection> result = commentService.getProjectionsByPostWithPaging(post.getId(), pageable);

                // then
                assertThat(result).isNotNull();
                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent().get(0).content()).isEqualTo("첫 번째 댓글");
                assertThat(result.getContent().get(1).content()).isEqualTo("두 번째 댓글");
                assertThat(result.getTotalElements()).isEqualTo(2);
        }
}
