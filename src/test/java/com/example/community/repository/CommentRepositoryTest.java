package com.example.community.repository;

import com.example.community.domain.Comment;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.dto.CommentProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.test.context.ActiveProfiles("test")
class CommentRepositoryTest {

    @Autowired
    private CommentRepository comments;
    @Autowired
    private PostRepository posts;
    @Autowired
    private MemberRepository members;

    @Test
    @DisplayName("프로젝션 기반 댓글 페이징 조회")
    void find_projections_by_post() {
        Member m = members.save(
                Member.builder().username("u1").email("u1@test.com").password("p").roles(Set.of("ROLE_USER")).build());
        Post p = posts.save(Post.builder().title("t").content("c").author(m).build());
        comments.save(Comment.builder().post(p).author(m).content("c1").build());
        comments.save(Comment.builder().post(p).author(m).content("c2").build());

        Page<CommentProjection> page = comments.findProjectionsByPostId(p.getId(), PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).postId()).isEqualTo(p.getId());
    }
}
