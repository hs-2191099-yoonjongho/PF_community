package com.example.community.service;

import com.example.community.auth.Actor;
import com.example.community.service.exception.InvalidImageException;

import com.example.community.service.exception.ForbiddenOperationException;
import com.example.community.common.FilePolicy;
import com.example.community.domain.BoardType;
import com.example.community.domain.Member;
import com.example.community.domain.Post;
import com.example.community.domain.PostImage;
import com.example.community.repository.MemberRepository;
import com.example.community.repository.PostRepository;
import com.example.community.service.dto.PostDtos;
import com.example.community.service.dto.PostSummaryDto;
import com.example.community.service.exception.EntityNotFoundException;
import com.example.community.storage.Storage;
import com.example.community.util.PageableUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 게시글 관련 비즈니스 로직 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository posts;
    private final MemberRepository members;
    private final Storage storage;

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 5000;

    /**
     * 제목 정규화 및 검증
     * @param raw 입력 제목
     * @return 정규화된 제목
     * @throws IllegalArgumentException 제목이 비어있거나 길이 초과 시 발생
     */
    private String normalizeAndValidateTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty())
            throw new IllegalArgumentException("제목은 비어 있을 수 없습니다.");
        if (title.length() > MAX_TITLE_LENGTH)
            throw new IllegalArgumentException("제목은 " + MAX_TITLE_LENGTH + "자를 초과할 수 없습니다.");
        return title;
    }

    /**
     * 본문 정규화 및 검증
     * @param raw 입력 본문
     * @return 정규화된 본문
     * @throws IllegalArgumentException 본문이 비어있거나 길이 초과 시 발생
     */
    private String normalizeAndValidateContent(String raw) {
        String content = raw == null ? "" : raw.trim();
        if (content.isEmpty())
            throw new IllegalArgumentException("본문은 비어 있을 수 없습니다.");
        if (content.length() > MAX_CONTENT_LENGTH)
            throw new IllegalArgumentException("본문은 " + MAX_CONTENT_LENGTH + "자를 초과할 수 없습니다.");
        return content;
    }

    /**
     * 파일 키에서 Content-Type을 유추합니다.
     * @param key 파일 키
     * @return Content-Type 문자열
     */
    private String determineContentTypeFromKey(String key) {
        String lowerKey = key.toLowerCase();
        if (lowerKey.endsWith(".jpg") || lowerKey.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerKey.endsWith(".png")) {
            return "image/png";
        } else if (lowerKey.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerKey.endsWith(".webp")) {
            return "image/webp";
        } else {
            // 기본값
            return "application/octet-stream";
        }
    }

    /**
     * 게시글에 이미지 추가를 위한 유틸리티 메서드
     * 이미지 키를 기반으로 PostImage 엔티티를 생성합니다.
     * @param post 게시글 엔티티
     * @param key 이미지 파일 키
     * @return 생성된 PostImage 엔티티
     * @throws InvalidImageException 이미지 처리 중 오류 발생 시
     */
    private PostImage createPostImageFromKey(Post post, String key) {
        try {
            if (!storage.exists(key)) {
                throw new InvalidImageException("이미지 처리 중 오류가 발생했습니다");
            }
            String url = storage.url(key);
            String originalName = key.substring(key.lastIndexOf('/') + 1);
            String contentType = determineContentTypeFromKey(key);
            long size = 0;
            return PostImage.builder()
                    .post(post)
                    .fileKey(key)
                    .originalName(originalName)
                    .contentType(contentType)
                    .size(size)
                    .url(url)
                    .build();
        } catch (Exception e) {
            log.error("이미지 처리 중 오류: 파일키={}, 오류={}", key, e.getMessage());
            throw new InvalidImageException("이미지 처리 중 오류가 발생했습니다", e);
        }
    }


    /**
     * 게시글 생성 (Actor 기반, MemberDetails 변환 없이 직접 처리)
     * 공지사항(NOTICE)은 관리자만 작성 가능하도록 서비스 레이어에서도 검증합니다.
     * @param actor 작성자 정보
     * @param req 게시글 생성 요청 DTO
     * @return 생성된 게시글 엔티티
     * @throws EntityNotFoundException 작성자 미존재 시
     * @throws ForbiddenOperationException 권한 없는 사용자가 공지사항 작성 시
     * @throws IllegalArgumentException 입력값 오류 시
     */
    @Transactional
    public Post create(Actor actor, PostDtos.Create req) {
        Member authorEntity = members.findById(actor.id())
                .orElseThrow(() -> new EntityNotFoundException("작성자", actor.id()));

            // boardType null 방어 (fail-fast)
        if (req.boardType() == null) {
            throw new IllegalArgumentException("게시판 유형은 필수입니다.");
        }

            // 이미지 개수 10개 초과 방어 (fail-fast)
        if (req.imageKeys() != null && req.imageKeys().size() > 10) {
            throw new IllegalArgumentException("이미지는 최대 10개까지 첨부 가능합니다.");
        }

    // 입력 정규화/검증
        String title = normalizeAndValidateTitle(req.title());
        String content = normalizeAndValidateContent(req.content());

    // 공지사항은 관리자만 작성 가능
        if (req.boardType() == BoardType.NOTICE && !actor.roles().contains("ROLE_ADMIN")) {
            throw new ForbiddenOperationException("공지사항은 관리자만 작성할 수 있습니다");
        }

        Post p = Post.builder()
                .author(authorEntity)
                .title(title)
                .content(content)
                .viewCount(0)
                .boardType(req.boardType())
                .build();

        if (req.imageKeys() != null && !req.imageKeys().isEmpty()) {
            String ownerPrefix = FilePolicy.POST_IMAGES_PATH + "/" + actor.id() + "/";
            Set<String> processedKeys = new HashSet<>();
            for (String key : req.imageKeys()) {
                try {
                    if (!processedKeys.add(key)) {
                        log.debug("중복 이미지 키 무시: {}", key);
                        continue;
                    }
                    if (!FilePolicy.isPathSafe(key)) {
                        throw new InvalidImageException("이미지 처리 중 오류가 발생했습니다");
                    }
                    if (!key.startsWith(ownerPrefix)) {
                        throw new InvalidImageException("이미지 처리 중 오류가 발생했습니다");
                    }
                    PostImage image = createPostImageFromKey(p, key);
                    p.addImage(image);
                } catch (InvalidImageException e) {
                    log.error("이미지 처리 중 오류: 파일키={}, 오류={}", key, e.getMessage());
                    throw e;
                }
            }
        }
        return posts.save(p);
    }


    /**
     * 제목 또는 내용에 특정 검색어가 포함된 게시글을 조회합니다 (요약 정보 반환)
     * @param query 검색어 (null이면 전체 조회)
     * @param pageable 페이징 정보
     * @return 게시글 요약 정보 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> searchSummary(String query, Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);

        // 검색어가 없는 경우 전체 조회
        Page<Post> postPage;
        if (query == null || query.isBlank()) {
            postPage = posts.findAllWithAuthor(safePageable);
        } else {
            postPage = posts.findByTitleOrContentContainingIgnoreCaseWithAuthor(query, safePageable);
        }

        // DTO로 변환하여 반환
        return postPage.map(post -> PostSummaryDto.from(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBoardType()));
    }

    /**
     * 게시글 조회 및 조회수 증가
     * @param id 게시글 ID
     * @return 이미지와 작성자 정보가 포함된 게시글 엔티티
     * @throws EntityNotFoundException 게시글이 존재하지 않을 경우
     */
    @Transactional
    public Post getAndIncrementViewCount(Long id) {
        // 이미지를 함께 로드하는 새 메서드 사용
        Post post = posts.findByIdWithAuthorAndImages(id)
                .orElseThrow(() -> new EntityNotFoundException("게시글", id));

    // 별도 쿼리로 조회수 증가 (낙관적 락 충돌 방지)
        posts.incrementViews(id);

    // 메모리상 객체도 증가 (응답 일관성 보장)
        post.incrementViewCount();

        return post;
    }


    /**
     * 특정 좋아요 수 이상의 인기/베스트 게시글 조회 (요약 정보 반환)
     * @param minLikeCount 최소 좋아요 수 (인기 게시글: 10, 베스트 게시글: 30)
     * @param pageable 페이징 정보
     * @return 인기/베스트 게시글 페이지
     */
    @Transactional(readOnly = true)
    private Page<PostSummaryDto> getPostsByMinLikes(long minLikeCount, Pageable pageable) {
    // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);

    // 요약 정보 조회 시 작성자 정보만 함께 로딩하는 최적화된 쿼리 사용
        Page<Post> postPage = posts.findWithAuthorByLikeCountAndQuery("", minLikeCount, safePageable);

    // DTO로 변환하여 반환
        return postPage.map(post -> PostSummaryDto.from(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBoardType()));
    }

    /**
     * 인기 게시글 조회 (요약 정보 반환)
     * @param pageable 페이징 정보
     * @return 인기 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> getPopularPostsSummary(Pageable pageable) {
        return getPostsByMinLikes(10L, pageable);
    }

    /**
     * 베스트 게시글 조회 (요약 정보 반환)
     * @param pageable 페이징 정보
     * @return 베스트 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> getBestPostsSummary(Pageable pageable) {
        return getPostsByMinLikes(30L, pageable);
    }

    /**
    * 게시글 업데이트 (내용과 이미지 동시 처리)
    * @param postId 게시글 ID
    * @param memberId 작성자 ID
     * @param req 게시글 수정 요청 DTO
     * @return 수정된 게시글 엔티티
     * @throws ForbiddenOperationException 권한 없는 사용자가 수정 시
     * @throws EntityNotFoundException 게시글 미존재 시
     * @throws IllegalArgumentException 입력값 오류 시
     */
    @Transactional
    public Post update(Long postId, Long memberId, PostDtos.Update req) {
        // fail-fast: 이미지 개수 10개 초과 방어
        if (req.imageKeys() != null && req.imageKeys().size() > 10) {
            throw new IllegalArgumentException("이미지는 최대 10개까지 첨부 가능합니다.");
        }
        // 잠금 기반 id+authorId 조건 조회
        Post p = posts.findByIdAndAuthorIdForUpdate(postId, memberId)
                .orElseThrow(() -> {
                    // postId만 존재하면 권한 없음, postId 자체가 없으면 404
                    if (posts.existsById(postId))
                        return new ForbiddenOperationException("수정 권한이 없습니다.");
                    else
                        return new EntityNotFoundException("게시글", postId);
                });
        // 제목/본문 정규화 및 검증
        String title = normalizeAndValidateTitle(req.title());
        String content = normalizeAndValidateContent(req.content());
        p.updateContent(title, content);
        // 이미지 처리 (기존 로직과 동일)
        if (req.imageKeys() != null) {
            List<PostImage> oldImages = new ArrayList<>(p.getImages());
            List<String> keysToDelete = new ArrayList<>();
            for (PostImage oldImage : oldImages) {
                try {
                    boolean shouldKeep = req.imageKeys().contains(oldImage.getFileKey());
                    if (!shouldKeep) {
                        p.removeImage(oldImage);
                        keysToDelete.add(oldImage.getFileKey());
                    }
                } catch (Exception e) {
                    log.error("이미지 삭제 준비 실패: 이미지키={}, 오류={}", oldImage.getFileKey(), e.getMessage());
                }
            }
            Set<String> processedKeys = new HashSet<>();
            for (String key : req.imageKeys()) {
                if (!processedKeys.add(key)) {
                    log.debug("중복 이미지 키 무시: {}", key);
                    continue;
                }
                boolean alreadyLinked = p.getImages().stream()
                        .anyMatch(img -> img.getFileKey().equals(key));
                if (!alreadyLinked) {
                    try {
                        String expectedPathPattern = FilePolicy.POST_IMAGES_PATH + "/" + p.getAuthor().getId() + "/";
                        if (!FilePolicy.isPathSafe(key)) {
                            throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
                        }
                        if (!key.startsWith(expectedPathPattern)) {
                            throw new RuntimeException("이미지 소유권 검증 실패: 작성자 ID와 파일 경로가 일치하지 않습니다");
                        }
                        PostImage image = createPostImageFromKey(p, key);
                        p.addImage(image);
                    } catch (Exception e) {
                        log.error("새 이미지 추가 실패: 파일키={}, 오류={}", key, e.getMessage());
                        throw new RuntimeException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
                    }
                }
            }
            if (!keysToDelete.isEmpty()) {
                final List<String> finalKeysToDelete = new ArrayList<>(keysToDelete);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String key : finalKeysToDelete) {
                            try {
                                storage.delete(key);
                                log.debug("트랜잭션 커밋 후 이미지 삭제 성공: 이미지키={}", key);
                            } catch (Exception e) {
                                log.error("트랜잭션 커밋 후 이미지 삭제 실패: 이미지키={}, 오류={}", key, e.getMessage());
                            }
                        }
                    }
                });
            }
        }
        return p;
    }

    /**
    * Actor 기반 게시글 삭제
    * @param postId 게시글 ID
    * @param actor 요청자 정보
     * @throws ForbiddenOperationException 권한 없는 사용자가 삭제 시
     * @throws EntityNotFoundException 게시글 미존재 시
     */
    @Transactional
    public void delete(Long postId, Actor actor) {
        final boolean isAdmin = actor.isAdmin();
        Post post = (isAdmin ? posts.findByIdForUpdate(postId) : posts.findByIdAndAuthorIdForUpdate(postId, actor.id()))
                .orElseThrow(() -> {
                    if (isAdmin) {
                        return new EntityNotFoundException("게시글", postId);
                    } else {
                        if (posts.existsById(postId))
                            return new ForbiddenOperationException("삭제 권한이 없습니다.");
                        else
                            return new EntityNotFoundException("게시글", postId);
                    }
                });
        final List<String> keysToDelete = post.getImages().stream()
                .map(PostImage::getFileKey)
                .collect(java.util.stream.Collectors.toList());
        posts.delete(post);
        if (!keysToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : keysToDelete) {
                        try {
                            storage.delete(key);
                            log.debug("트랜잭션 커밋 후 이미지 삭제 성공: 게시글ID={}, 이미지키={}", postId, key);
                        } catch (Exception e) {
                            log.error("트랜잭션 커밋 후 이미지 삭제 실패: 게시글ID={}, 이미지키={}, 오류={}", postId, key, e.getMessage());
                        }
                    }
                }
            });
        }
    }


    /**
     * 최근 N일 내 추천순 게시글 조회 (요약 정보 반환)
     * @param days 조회할 일수 (1~365)
     * @param pageable 페이징 정보
     * @return 추천순 게시글 목록
     * @throws IllegalArgumentException days 범위 오류 시
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> getRecentRecommendedSummary(int days, Pageable pageable) {
        // 날짜 범위 검증
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365, but was: " + days);
        }

    // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);

        LocalDateTime from = LocalDateTime.now().minusDays(days);

    // 요약 정보 조회 시 작성자 정보만 함께 로딩하는 최적화된 쿼리 사용
        Page<Post> postPage = posts.findRecentWithAuthorOrderByLikes(from, safePageable);

    // DTO로 변환하여 반환
        return postPage.map(post -> PostSummaryDto.from(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBoardType()));
    }

    /**
     * 특정 추천수 이상인 게시글 중 검색어를 포함하는 게시글 조회 (요약 정보 반환)
     * @param query 검색어 (null이면 전체 조회)
     * @param minLikes 최소 추천수
     * @param pageable 페이징 정보
     * @return 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> searchWithMinLikesSummary(String query, long minLikes, Pageable pageable) {
    // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);

        // 요약 정보 조회 시 작성자 정보만 함께 로딩하는 최적화된 쿼리 사용
        Page<Post> postPage = posts.findWithAuthorByLikeCountAndQuery(query, minLikes, safePageable);

        // DTO로 변환하여 반환
        return postPage.map(post -> PostSummaryDto.from(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBoardType()));
    }

    /**
     * 게시판 타입별 게시글 목록 조회 (요약 정보 반환)
     * @param boardType 게시판 타입
     * @param q 검색어 (null이면 전체 조회)
     * @param pageable 페이징 정보
     * @return 게시글 페이지
     */
    @Transactional(readOnly = true)
    public Page<PostSummaryDto> searchByBoardTypeSummary(BoardType boardType, String q, Pageable pageable) {
        // 정렬 필드 화이트리스트 검증
        Pageable safePageable = PageableUtil.getSafePostPageable(pageable);

    // 요약 정보 조회를 위한 최적화된 쿼리 사용
        Page<Post> postPage = posts.findWithAuthorByBoardTypeAndQuery(boardType, q, safePageable);

    // DTO로 변환하여 반환
        return postPage.map(post -> PostSummaryDto.from(
                post.getId(),
                post.getTitle(),
                post.getAuthor() != null ? post.getAuthor().getUsername() : null,
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getBoardType()));
    }

}
