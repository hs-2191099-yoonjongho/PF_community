package com.example.community.service.dto;

import com.example.community.domain.BoardType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

public class PostDtos {
    public record Create(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content,
            @NotNull BoardType boardType,
            List<ImageMeta> images
    ) {
        /**
         * 자유게시판(FREE) 타입으로 게시글 생성 DTO 생성
         */
        public static Create free(String title, String content) {
            return new Create(title, content, BoardType.FREE, new ArrayList<>());
        }
        
        /**
         * 공지사항(NOTICE) 타입으로 게시글 생성 DTO 생성
         */
        public static Create notice(String title, String content) {
            return new Create(title, content, BoardType.NOTICE, new ArrayList<>());
        }
    }
    
    public record Update(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content,
            List<ImageMeta> images
    ) {}
}
