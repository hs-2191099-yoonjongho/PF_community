package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.service.dto.PostSummaryDto;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 게시글 목록 조회 시 사용하는 경량화된 응답 DTO
 * 게시글 내용(content) 필드를 제외하여 데이터 전송량을 줄입니다.
 * 썸네일 기능을 사용하지 않으므로 이미지 필드 제거
 */
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값을 가진 필드는 JSON 직렬화에서 제외
public record PostSummaryRes(
        Long id, 
        String title,
        String authorName,
        long viewCount, 
        long likeCount, 
        BoardType boardType, 
        String boardTypeDescription,
        LocalDateTime createdAt
) {
    public static PostSummaryRes of(PostSummaryDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PostSummaryDto cannot be null");
        }
        
        // id는 필수 값이므로 null인 경우 예외 발생
        if (dto.id() == null) {
            throw new IllegalArgumentException("Post id cannot be null");
        }
        
        // title이 null인 경우 빈 문자열로 대체 (NPE 방지)
        String safeTitle = dto.title() != null ? dto.title() : "";
        
        // authorName이 null인 경우 빈 문자열로 대체
        String safeAuthorName = dto.authorName() != null ? dto.authorName() : "";
        
        // createdAt은 필수 값이므로 null인 경우 예외 발생
        if (dto.createdAt() == null) {
            throw new IllegalArgumentException("Post createdAt cannot be null");
        }
        
        return new PostSummaryRes(
                dto.id(),
                safeTitle,
                safeAuthorName,
                dto.viewCount(),
                dto.likeCount(),
                dto.boardType(),
                dto.boardType() != null ? dto.boardType().getDescription() : null,
                dto.createdAt()
        );
    }
}
