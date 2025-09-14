package com.example.community.service;

import com.example.community.domain.BoardType;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import com.example.community.common.FilePolicy;
import com.example.community.storage.Storage;
import com.example.community.auth.Actor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private Storage storage;
    @InjectMocks
    private PostService postService;
    private Member testMember;
    private Actor actor;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .roles(Set.of("ROLE_USER"))
                .build();
        actor = new Actor(1L, Set.of("ROLE_USER"));
    }

    @Test
    @DisplayName("게시글 생성 - 정상 이미지 경로 1건 첨부")
    void createPost_withOwnedImage_attachesImage() {
        String key = FilePolicy.POST_IMAGES_PATH + "/" + actor.id() + "/img1.jpg";
        when(memberRepository.findById(actor.id())).thenReturn(Optional.of(testMember));
        when(storage.exists(key)).thenReturn(true);
        when(storage.url(key)).thenReturn("http://x/img1.jpg");
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        Post res = postService.create(actor, new PostDtos.Create("t", "c", BoardType.FREE, List.of(key)));

        assertThat(res.getImages()).hasSize(1);
        assertThat(res.getImages().get(0).getFileKey()).isEqualTo(key);
        verify(storage).url(key);
    }
}