package com.example.community.repository;

import com.example.community.domain.BoardType;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
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
class PostRepositoryTest {

    @Autowired
    private PostRepository posts;
    @Autowired
    private MemberRepository members;

    @Test
    @DisplayName("BoardType별 페이징 조회 동작")
    void find_by_boardType() {
        Member m = members.save(
                Member.builder().username("u1").email("u1@test.com").password("p").roles(Set.of("ROLE_USER")).build());
        posts.save(Post.builder().title("a").content("c").boardType(BoardType.FREE).author(m).build());
        posts.save(Post.builder().title("b").content("c").boardType(BoardType.FREE).author(m).build());

        Page<Post> page = posts.findWithAuthorByBoardTypeAndQuery(BoardType.FREE, "", PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }
}
