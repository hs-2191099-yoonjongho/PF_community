package com.example.community.service;

import com.example.community.domain.BoardType;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostImageRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import com.example.community.service.dto.PostSummaryDto;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.storage.Storage;
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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PostImageRepository postImageRepository;

    @Mock
    private Storage storage;

    @InjectMocks
    private PostService postService;

    private Member testMember;
    private Member adminMember;

    @BeforeEach
    void setUp() {
        // 일반 사용자 멤버 설정
        testMember = Member.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("password")
                .roles(Set.of("ROLE_USER"))
                .build();

        // 관리자 멤버 설정
        adminMember = Member.builder()
                .id(2L)
                .username("admin")
                .email("admin@example.com")
                .password("password")
                .roles(Set.of("ROLE_USER", "ROLE_ADMIN"))
                .build();
    }

    @Test
    @DisplayName("일반 게시글 생성 성공 - 이미지 없음")
    void createPostWithoutImages() {
        // given
        Long authorId = 1L;
        PostDtos.Create createDto = new PostDtos.Create(
                "테스트 제목",
                "테스트 내용입니다.",
                BoardType.FREE,
                null
        );

        when(memberRepository.findById(authorId)).thenReturn(Optional.of(testMember));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            // ID가 리플렉션으로 설정된 새 Post 객체 생성 (테스트용)
            Post savedPost = Post.builder()
                    .id(5L)
                    .title(post.getTitle())
                    .content(post.getContent())
                    .author(post.getAuthor())
                    .boardType(post.getBoardType())
                    .viewCount(post.getViewCount())
                    .build();
            return savedPost;
        });

        // when
        Post result = postService.create(authorId, createDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("테스트 제목");
        assertThat(result.getContent()).isEqualTo("테스트 내용입니다.");
        assertThat(result.getBoardType()).isEqualTo(BoardType.FREE);
        assertThat(result.getAuthor()).isEqualTo(testMember);
        assertThat(result.getImages()).isEmpty();
        assertThat(result.getViewCount()).isEqualTo(0);

        verify(memberRepository).findById(authorId);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 게시글 생성 시 예외 발생")
    void createPostWithNonExistentUser() {
        // given
        Long nonExistentUserId = 999L;
        PostDtos.Create createDto = new PostDtos.Create(
                "테스트 제목",
                "테스트 내용입니다.",
                BoardType.FREE,
                null
        );

        when(memberRepository.findById(nonExistentUserId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> postService.create(nonExistentUserId, createDto))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("작성자");

        verify(memberRepository).findById(nonExistentUserId);
    }

    @Test
    @DisplayName("일반 사용자가 공지사항 작성 시 예외 발생")
    void createNoticeByNonAdmin() {
        // given
        Long regularUserId = 1L;
        PostDtos.Create createDto = new PostDtos.Create(
                "공지사항 제목",
                "공지사항 내용입니다.",
                BoardType.NOTICE,
                null
        );

        when(memberRepository.findById(regularUserId)).thenReturn(Optional.of(testMember));

        // when & then
        assertThatThrownBy(() -> postService.create(regularUserId, createDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("공지사항은 관리자만 작성할 수 있습니다");

        verify(memberRepository).findById(regularUserId);
    }

    @Test
    @DisplayName("관리자가 공지사항 작성 성공")
    void createNoticeByAdmin() {
        // given
        Long adminId = 2L;
        PostDtos.Create createDto = new PostDtos.Create(
                "공지사항 제목",
                "공지사항 내용입니다.",
                BoardType.NOTICE,
                null
        );

        when(memberRepository.findById(adminId)).thenReturn(Optional.of(adminMember));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            // ID가 리플렉션으로 설정된 새 Post 객체 생성 (테스트용)
            Post savedPost = Post.builder()
                    .id(6L)
                    .title(post.getTitle())
                    .content(post.getContent())
                    .author(post.getAuthor())
                    .boardType(post.getBoardType())
                    .viewCount(post.getViewCount())
                    .build();
            return savedPost;
        });

        // when
        Post result = postService.create(adminId, createDto);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(6L);
        assertThat(result.getTitle()).isEqualTo("공지사항 제목");
        assertThat(result.getContent()).isEqualTo("공지사항 내용입니다.");
        assertThat(result.getBoardType()).isEqualTo(BoardType.NOTICE);
        assertThat(result.getAuthor()).isEqualTo(adminMember);

        verify(memberRepository).findById(adminId);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    @DisplayName("게시글 조회수 증가 확인")
    void getAndIncrementViewCount() {
        // given
        Long postId = 1L;
        Post post = Post.builder()
                .id(postId)
                .title("테스트 게시글")
                .content("조회수 테스트")
                .author(testMember)
                .viewCount(10)
                .boardType(BoardType.FREE)
                .build();

        when(postRepository.findByIdWithAuthorAndImages(postId)).thenReturn(Optional.of(post));

        // when
        Post result = postService.getAndIncrementViewCount(postId);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getViewCount()).isEqualTo(11); // 조회수 증가 확인
        
        verify(postRepository).findByIdWithAuthorAndImages(postId);
        verify(postRepository).incrementViews(postId);
    }
    
    @Test
    @DisplayName("게시글 검색 기능 테스트")
    void searchPosts() {
        // given
        String searchQuery = "테스트";
        Pageable pageable = PageRequest.of(0, 10);
        
        Post post1 = Post.builder()
                .id(1L)
                .title("테스트 게시글 1")
                .content("테스트 내용입니다.")
                .author(testMember)
                .viewCount(5)
                .boardType(BoardType.FREE)
                .build();
                
        Post post2 = Post.builder()
                .id(2L)
                .title("두 번째 게시글")
                .content("이 글에도 테스트라는 단어가 있습니다.")
                .author(testMember)
                .viewCount(3)
                .boardType(BoardType.FREE)
                .build();
                
        List<Post> posts = Arrays.asList(post1, post2);
        Page<Post> postPage = new PageImpl<>(posts, pageable, posts.size());
        
        when(postRepository.findByTitleOrContentContainingIgnoreCaseWithAuthor(eq(searchQuery), any(Pageable.class)))
                .thenReturn(postPage);
                
        // when
        Page<PostSummaryDto> result = postService.searchSummary(searchQuery, pageable);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).title()).isEqualTo("테스트 게시글 1");
        
        verify(postRepository).findByTitleOrContentContainingIgnoreCaseWithAuthor(eq(searchQuery), any(Pageable.class));
    }
}
