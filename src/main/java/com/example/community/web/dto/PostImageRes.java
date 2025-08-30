package com.example.community.web.dto;

import com.example.community.domain.PostImage;

public record PostImageRes(
    String fileKey,
    String url
) {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PostImageRes.class);
    
    public static PostImageRes of(PostImage image) {
        if (image == null) {
            log.warn("이미지 변환 시도 중 null 이미지 발견");
            return null;
        }
        
        try {
            return new PostImageRes(
                image.getFileKey(),
                image.getUrl()
            );
        } catch (Exception e) {
            log.error("이미지 변환 오류: {}", e.getMessage());
            throw e;
        }
    }
}
