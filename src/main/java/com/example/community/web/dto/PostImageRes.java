package com.example.community.web.dto;

import com.example.community.domain.PostImage;

public record PostImageRes(
    Long id,
    String fileKey,
    String originalName,
    String contentType,
    long size,
    String url
) {
    public static PostImageRes of(PostImage image) {
        return new PostImageRes(
            image.getId(),
            image.getFileKey(),
            image.getOriginalName(),
            image.getContentType(),
            image.getSize(),
            image.getUrl()
        );
    }
}
