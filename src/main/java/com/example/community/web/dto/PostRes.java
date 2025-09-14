package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.domain.Post;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 게시글 정보를 위한 응답 DTO
 * 클라이언트에 전송할 게시글 데이터를 포함
 */

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
        LocalDateTime createdAt) {
    /**
     * Post 엔티티로부터 응답 DTO 생성
     * 
     * @param p 변환할 게시글 엔티티
     * @return 게시글 정보가 담긴 DTO
     * @throws IllegalArgumentException post 또는 필수 관계(author, boardType)가 null인 경우
     */
    public static PostRes of(Post p) {
        if (p == null) {
            throw new IllegalArgumentException("Post cannot be null");
        }

        if (p.getAuthor() == null) {
            throw new IllegalArgumentException("Post author cannot be null");
        }

        if (p.getBoardType() == null) {
            throw new IllegalArgumentException("Post boardType cannot be null");
        }

        // 이미지 변환 - 예외를 삼키지 않고 fail-fast 원칙 적용
        List<PostImageRes> imageList;
        if (p.getImages() == null || p.getImages().isEmpty()) {
            imageList = List.of(); // 불변 빈 리스트
        } else {
            imageList = p.getImages().stream()
                    .map(PostImageRes::of) // 예외가 발생하면 상위로 전파됨
                    .collect(Collectors.toUnmodifiableList()); // 불변 리스트로 수집
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
                p.getCreatedAt());
    }
}