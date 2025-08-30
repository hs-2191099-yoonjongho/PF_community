package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record PostRes(
        Long id, 
        String title, 
        String content, 
        MemberRes author, 
        long viewCount, 
        long likeCount, 
        BoardType boardType, 
        String boardTypeDescription,
        List<PostImageRes> images,
        LocalDateTime createdAt
) {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostRes.class);
    
    public static PostRes of(Post p) {
        List<PostImageRes> imageList = new ArrayList<>();
        try {
            if (p.getImages() != null && !p.getImages().isEmpty()) {
                imageList = p.getImages().stream()
                    .map(img -> {
                        try {
                            return PostImageRes.of(img);
                        } catch (Exception e) {
                            // 개별 이미지 변환 중 오류 발생 시 로깅하고 해당 이미지는 건너뜀
                            log.warn("이미지 변환 오류 (게시글 ID={}): {}", p.getId(), e.getMessage());
                            return null;
                        }
                    })
                    .filter(img -> img != null)
                    .collect(Collectors.toList());
            }
        } catch (Exception e) {
            // 이미지 목록 처리 중 예외 발생 시 빈 목록 사용
            log.error("이미지 목록 처리 중 오류 (게시글 ID={}): {}", p.getId(), e.getMessage());
            imageList = new ArrayList<>();
        }
        
        return new PostRes(
                p.getId(),
                p.getTitle(),
                p.getContent(),
                MemberRes.of(p.getAuthor()),
                p.getViewCount(),
                p.getLikeCount(),
                p.getBoardType(),
                p.getBoardType().getDescription(),
                imageList,
                p.getCreatedAt()
        );
    }
}