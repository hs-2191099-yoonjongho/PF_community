package com.example.community.web.dto;

import com.example.community.domain.PostImage;

public record PostImageRes(
        String fileKey,
        String url) {
    /**
     * PostImage 엔티티로부터 응답 DTO 생성
     * 
     * @param image 변환할 이미지 엔티티
     * @return 이미지 정보가 담긴 DTO
     * @throws IllegalArgumentException image가 null이거나 fileKey가 null/blank인 경우
     */
    public static PostImageRes of(PostImage image) {
        if (image == null) {
            throw new IllegalArgumentException("PostImage cannot be null");
        }
        String fileKey = image.getFileKey();
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("PostImage fileKey cannot be null or blank");
        }
        return new PostImageRes(
                fileKey,
                image.getUrl());
    }
}
