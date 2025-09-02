package com.example.community.service;

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

    //===================================================================
    // 0. 유틸리티 메서드
    //===================================================================
    
    /**
     * 파일 키에서 Content-Type 유추
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
     * 
     * @param post 게시글 엔티티
     * @param key 이미지 파일 키
     * @return 생성된 PostImage 엔티티
     * @throws IllegalArgumentException 이미지 처리 중 오류 발생 시
     */
    private PostImage createPostImageFromKey(Post post, String key) {
        try {
            // 파일 존재 확인
            if (!storage.exists(key)) {
                throw new IllegalArgumentException("존재하지 않는 이미지: " + key);
            }
            
            // 파일 메타데이터 조회
            String url = storage.url(key);
            String originalName = key.substring(key.lastIndexOf('/') + 1);
            
            // URL에서 파일 확장자 추출
            String contentType = determineContentTypeFromKey(key);
            long size = 0; // 실제로는 파일 크기를 얻는 메서드가 필요할 수 있음
            
            // 이미지 엔티티 생성 및 반환
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
            throw new IllegalArgumentException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    //===================================================================
    // 1. 기본 CRUD 작업
    //===================================================================

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
        
        // 이미지 처리 - key 값만 사용하여 처리
        if (req.imageKeys() != null && !req.imageKeys().isEmpty()) {
            // 소유권 검증을 위한 경로 패턴 (작성자 ID 기반)
            String ownerPrefix = FilePolicy.POST_IMAGES_PATH + "/" + author.getId() + "/";
            
            // 중복 키 감지를 위한 처리 세트 - HashSet 사용하여 O(1) 조회 성능 보장
            Set<String> processedKeys = new HashSet<>();
            
            for (String key : req.imageKeys()) {
                try {
                    // 중복 키 확인 (같은 요청 내에서 중복 방지) - HashSet 활용으로 O(1) 성능
                    if (!processedKeys.add(key)) { // HashSet.add()는 이미 있으면 false 반환
                        log.debug("중복 이미지 키 무시: {}", key);
                        continue;
                    }
                    
                    // 키 유효성 검증
                    if (!FilePolicy.isPathSafe(key)) {
                        throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
                    }
                    
                    // 소유권 검증 - 작성자 ID 기반 경로 확인
                    if (!key.startsWith(ownerPrefix)) {
                        throw new IllegalArgumentException("이미지 소유권 검증 실패: 작성자 ID와 파일 경로가 일치하지 않습니다");
                    }
                    
                    // 이미지 엔티티 생성 및 연결 (추출된 메서드 사용)
                    PostImage image = createPostImageFromKey(p, key);
                    p.addImage(image);
                    
                    // 참고: 처리 완료된 키는 이미 위의 processedKeys.add(key) 호출에서 추가되었음
                } catch (Exception e) {
                    log.error("이미지 처리 중 오류: 파일키={}, 오류={}", key, e.getMessage());
                    throw new RuntimeException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
                }
            }
        }
        
        return posts.save(p);
    }

    //===================================================================
    // 2. 검색 및 조회 기능
    //===================================================================
    
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
        return postPage.map(PostSummaryDto::from);
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
    
    
    //===================================================================
    // 3. 인기 게시글 관련 기능
    //===================================================================
    
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
        return postPage.map(PostSummaryDto::from);
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
     * 게시글 업데이트
     * 게시글 내용과 이미지를 함께 업데이트합니다.
     */
    @Transactional
    public Post update(Long id, PostDtos.Update req) {
        Post p = posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("수정할 게시글", id));
        
        // 내용 업데이트
        p.updateContent(req.title(), req.content());
        
        // 이미지 처리
        if (req.imageKeys() != null) {
            // 기존 이미지 처리 (유지할 이미지는 남기고, 삭제할 이미지만 제거)
            List<PostImage> oldImages = new ArrayList<>(p.getImages());
            // 삭제할 이미지 키 모음 (트랜잭션 커밋 후 실제 삭제)
            List<String> keysToDelete = new ArrayList<>();
            
            for (PostImage oldImage : oldImages) {
                try {
                    // 새 이미지 목록에 포함되어 있는지 확인 (유지 여부)
                    boolean shouldKeep = req.imageKeys().contains(oldImage.getFileKey());
                    
                    if (!shouldKeep) {
                        // 유지하지 않을 이미지는 DB에서 연결 해제하고 삭제 목록에 추가
                        p.removeImage(oldImage); // 관계 제거
                        keysToDelete.add(oldImage.getFileKey()); // 커밋 후 삭제를 위해 저장
                    }
                } catch (Exception e) {
                    // 로깅
                    log.error("이미지 삭제 준비 실패: 이미지키={}, 오류={}", oldImage.getFileKey(), e.getMessage());
                }
            }
            
            // 새 이미지 추가 (기존에 없는 이미지만)
            // 중복 키 체크를 위한 HashSet - O(1) 성능
            Set<String> processedKeys = new HashSet<>();
            
            for (String key : req.imageKeys()) {
                // 이미 처리된 키는 건너뛰기
                if (!processedKeys.add(key)) {
                    log.debug("중복 이미지 키 무시: {}", key);
                    continue;
                }
                
                // 이미 연결되어 있는 이미지인지 확인
                boolean alreadyLinked = p.getImages().stream()
                        .anyMatch(img -> img.getFileKey().equals(key));
                
                if (!alreadyLinked) {
                    try {
                        // 소유권 검증 - 게시글 작성자 ID로 파일 경로 확인
                        String expectedPathPattern = FilePolicy.POST_IMAGES_PATH + "/" + p.getAuthor().getId() + "/";
                        
                        // 키 유효성 검증 (경로 보안 검사)
                        if (!FilePolicy.isPathSafe(key)) {
                            throw new IllegalArgumentException(FilePolicy.ERR_PATH_TRAVERSAL);
                        }
                        
                        // 소유권 검증
                        if (!key.startsWith(expectedPathPattern)) {
                            throw new RuntimeException("이미지 소유권 검증 실패: 작성자 ID와 파일 경로가 일치하지 않습니다");
                        }
                        
                        // 이미지 엔티티 생성 및 연결 (추출된 메서드 사용)
                        PostImage image = createPostImageFromKey(p, key);
                        p.addImage(image);
                    } catch (Exception e) {
                        log.error("새 이미지 추가 실패: 파일키={}, 오류={}", key, e.getMessage());
                        throw new RuntimeException("이미지 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
                    }
                }
            }
            
            // 트랜잭션 커밋 후 파일 실제 삭제 수행
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

    @Transactional
    public void delete(Long id) {
        // 게시글 조회
        Post post = posts.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("삭제할 게시글", id));
        
        // 삭제할 이미지 키 수집
        final List<String> keysToDelete = post.getImages().stream()
                .map(PostImage::getFileKey)
                .collect(java.util.stream.Collectors.toList());
        
        // 게시글 삭제 (이미지는 CASCADE로 자동 삭제)
        // posts.deleteById(id) 대신 엔티티 객체를 직접 전달하여 삭제
        posts.delete(post);
        
        // 트랜잭션 커밋 후 파일 실제 삭제 수행
        if (!keysToDelete.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String key : keysToDelete) {
                        try {
                            storage.delete(key);
                            log.debug("트랜잭션 커밋 후 이미지 삭제 성공: 게시글ID={}, 이미지키={}", id, key);
                        } catch (Exception e) {
                            log.error("트랜잭션 커밋 후 이미지 삭제 실패: 게시글ID={}, 이미지키={}, 오류={}", 
                                    id, key, e.getMessage());
                        }
                    }
                }
            });
        }
    }
    
    //===================================================================
    // 4. 최근 게시글 및 게시판 유형별 조회 기능
    //===================================================================
    

    /**
     * 최근 N일 내 추천순 게시글 조회 (요약 정보 반환)
     * @param days 조회할 일수 (1~365)
     * @param pageable 페이징 정보
     * @return 추천순 게시글 목록
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
        return postPage.map(PostSummaryDto::from);
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
        return postPage.map(PostSummaryDto::from);
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
        return postPage.map(PostSummaryDto::from);
    }
    
    
}
