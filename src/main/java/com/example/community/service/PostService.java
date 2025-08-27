package com.example.community.service;

import com.example.community.domain.BoardType;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.domain.PostImage;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostImageRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.ImageMeta;
import com.example.community.service.dto.PostDtos;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.storage.Storage;
import com.example.community.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {
    private final PostRepository posts;
    private final MemberRepository members;
    private final PostImageRepository postImages;
    private final Storage storage;

    /**
     * 게시글 생성
     * 공지사항(NOTICE)은 관리자만 작성 가능하도록 서비스 레이어에서도 검증
     */
    @Transactional
    public Post create(Long authorId, PostDtos.Create req) {
        Member author = members.findById(authorId)
                .orElseThrow(() -> new EntityNotFoundException("작성자", authorId));
        
        // 공지사항은 관리자만 작성 가능 (서비스 레이어에서도 검증)
        if (req.boardType() == BoardType.NOTICE) {
            if (!author.getRoles().contains("ROLE_ADMIN")) {
                throw new IllegalArgumentException("공지사항은 관리자만 작성할 수 있습니다");
            }
        }
        
        Post p = Post.builder()
                .author(author)
                .title(req.title())
                .content(req.content())
                .viewCount(0)
                .boardType(req.boardType())
                .build();
        
        // 이미지 처리
        if (req.images() != null && !req.images().isEmpty()) {
            for (ImageMeta meta : req.images()) {
                try {
                    // 이미지 엔티티 생성 및 연결
                    PostImage image = PostImage.builder()
                            .post(p)
                            .fileKey(meta.key())
                            .originalName(meta.name())
                            .contentType(meta.contentType())
                            .size(meta.size())
                            .url(meta.url())
                            .build();
                    
                    p.addImage(image);
                } catch (Exception e) {
                    throw new RuntimeException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
                }
            }
        }
        
        return posts.save(p);
    }

    /**
     * 게시글 검색
     * 제목 또는 내용에 검색어가 포함된 게시글을 조회합니다.
     * @param q 검색어 (null이면 전체 조회)
     * @param pageable 페이징 정보
     * @return 검색된 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<Post> search(String q, Pageable pageable) {
        // 정렬 필드 화이트리스트 검증 (이미 컨트롤러에서 적용되었을 수 있으나, 서비스 단에서도 보안 강화)
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        return (q == null || q.isBlank())
                ? posts.findAll(safePageable)
                : posts.findByTitleOrContentContainingIgnoreCaseWithAuthor(q, safePageable);
    }
    
    /**
     * 추천수 기반 필터링 검색
     * 최소 추천수 이상이면서 제목 또는 내용에 검색어가 포함된 게시글을 조회합니다.
     * @param q 검색어 (null이면 추천수만 기준으로 조회)
     * @param minLikes 최소 추천수
     * @param pageable 페이징 정보
     * @return 검색된 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<Post> searchWithMinLikes(String q, long minLikes, Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        return (q == null || q.isBlank())
                ? posts.findByLikeCountGreaterThanEqual(minLikes, safePageable)
                : posts.findByLikeCountGreaterThanEqualAndTitleOrContentContaining(minLikes, q, safePageable);
    }
    
    // 인기 게시글만 조회 (추천수 30 이상)
    @Transactional(readOnly = true)
    public Page<Post> getPopularPosts(Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        return posts.findByLikeCountGreaterThanEqual(30L, safePageable);
    }
    
    // 베스트 게시글만 조회 (추천수 100 이상)
    @Transactional(readOnly = true)
    public Page<Post> getBestPosts(Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        return posts.findByLikeCountGreaterThanEqual(100L, safePageable);
    }

    @Transactional(readOnly = true)
    public Post get(Long id) { 
        return posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글", id));
    }

    @Transactional
    public Post getAndIncrementViewCount(Long id) {
        Post post = posts.findByIdWithAuthor(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글", id));
        
        // 별도 쿼리로 조회수 증가 (낙관적 락 충돌 방지)
        posts.incrementViews(id);
        
        // 메모리상 객체도 증가 (응답 일관성 보장)
        post.incrementViewCount();
        
        return post;
    }

    @Transactional
    public Post update(Long id, PostDtos.Update req) {
        Post p = posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("수정할 게시글", id));
        
        // 내용 업데이트
        p.updateContent(req.title(), req.content());
        
        // 이미지 처리
        if (req.images() != null) {
            // 기존 이미지 모두 삭제 (기존 키는 보존 필요시 프론트에서 함께 보내줘야 함)
            List<PostImage> oldImages = new ArrayList<>(p.getImages());
            for (PostImage oldImage : oldImages) {
                try {
                    // 실제 파일 삭제 (새 이미지 목록에 없는 경우만)
                    boolean shouldKeep = req.images().stream()
                            .anyMatch(meta -> meta.key().equals(oldImage.getFileKey()));
                    
                    if (!shouldKeep) {
                        storage.delete(oldImage.getFileKey());
                    }
                    p.removeImage(oldImage);
                } catch (Exception e) {
                    // 로깅 필요
                }
            }
            
            // 새 이미지 추가
            for (ImageMeta meta : req.images()) {
                // 이미 존재하는 이미지인지 확인
                boolean exists = oldImages.stream()
                        .anyMatch(img -> img.getFileKey().equals(meta.key()));
                
                if (!exists) {
                    try {
                        // 이미지 엔티티 생성 및 연결
                        PostImage image = PostImage.builder()
                                .post(p)
                                .fileKey(meta.key())
                                .originalName(meta.name())
                                .contentType(meta.contentType())
                                .size(meta.size())
                                .url(meta.url())
                                .build();
                        
                        p.addImage(image);
                    } catch (Exception e) {
                        throw new RuntimeException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
                    }
                }
            }
        }
        
        return p;
    }

    @Transactional
    public void delete(Long id) {
        // 게시글 조회
        Post post = posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("삭제할 게시글", id));
        
        // 연결된 이미지 삭제
        for (PostImage image : post.getImages()) {
            try {
                storage.delete(image.getFileKey());
            } catch (Exception e) {
                // 로깅 필요
            }
        }
        
        // 게시글 삭제 (이미지는 CASCADE로 자동 삭제)
        posts.deleteById(id);
    }
    
    /**
     * 최근 N일 내 추천순 게시글 조회
     * @param days 조회할 일수 (1~365)
     * @param pageable 페이징 정보
     * @return 추천순 게시글 목록
     */
    @Transactional(readOnly = true)
    public Page<Post> getRecentRecommended(int days, Pageable pageable) {
        // 날짜 범위 검증
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365, but was: " + days);
        }
        
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        return posts.findRecentOrderByLikes(from, safePageable);
    }
    
    /**
     * 게시판 타입별 게시글 목록 조회
     * @param boardType 게시판 타입
     * @param q 검색어 (null이면 전체 조회)
     * @param pageable 페이징 정보
     * @return 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<Post> searchByBoardType(BoardType boardType, String q, Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);
        
        return (q == null || q.isBlank())
                ? posts.findByBoardType(boardType, safePageable)
                : posts.findByBoardTypeAndTitleOrContentContainingIgnoreCase(boardType, q, safePageable);
    }
}
