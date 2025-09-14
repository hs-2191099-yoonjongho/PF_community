package com.example.community.service.dto;

/**
 * 이미지 파일의 메타데이터를 담는 DTO
 * 파일의 식별 키와 접근 URL을 포함합니다.
 */
public record ImageMeta(
        /**
         * 이미지 파일의 식별 키
         * 파일 시스템 또는 스토리지 서비스에서 파일을 식별하는 데 사용됩니다.
         */
        String key,

        /**
         * 이미지 파일의 접근 URL
         * 클라이언트에서 이미지를 표시할 때 사용되는 전체 URL입니다.
         */
        String url) {
}
