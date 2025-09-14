package com.example.community.web.dto;

import com.example.community.domain.BoardType;
import com.example.community.service.dto.PostDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 게시글 관련 웹 계층 DTO 클래스
 * 웹 계층에서 유효성 검증을 담당합니다.
 */
public class PostWebDtos {
    /**
     * 게시글 생성 요청 DTO
     */
    public record CreateRequest(
            /**
             * 게시글 제목
             * 최대 200자까지 허용됩니다.
             */
            @NotBlank(message = "제목을 입력해주세요") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다") String title,

            /**
             * 게시글 본문 내용
             * 최대 5000자까지 허용됩니다.
             */
            @NotBlank(message = "내용을 입력해주세요") @Size(max = 5000, message = "내용은 최대 5000자까지 입력 가능합니다") String content,

            /**
             * 게시판 유형 (자유게시판, 공지사항 등)
             */
            @NotNull(message = "게시판 유형을 선택해주세요") BoardType boardType,

            /**
             * 게시글에 첨부된 이미지 파일 키 목록
             * 최대 10개까지 허용됩니다.
             * 각 키는 512자를 초과할 수 없고 비어있을 수 없습니다.
             */
            @Size(max = 10, message = "이미지는 최대 10개까지 첨부 가능합니다") List<@NotBlank(message = "이미지 키는 비어있을 수 없습니다") @Size(max = 512, message = "이미지 키는 최대 512자까지 허용됩니다") String> imageKeys) {
        /**
         * 서비스 계층 DTO로 변환
         */
        public PostDtos.Create toServiceDto() {
            // 문자열 입력 정규화 및 방어적 복사
            String safeTitle = title != null ? title.trim() : "";
            String safeContent = content != null ? content.trim() : "";

            // 이미지 키 정규화: 공백 제거, 중복 제거, null 안전
            List<String> safeImageKeys;
            if (imageKeys != null) {
                safeImageKeys = imageKeys.stream()
                        .filter(key -> key != null && !key.isBlank()) // null 또는 빈 문자열 제거
                        .map(String::trim) // 앞뒤 공백 제거
                        .distinct() // 중복 제거
                        .toList(); // 불변 리스트로 변환
            } else {
                safeImageKeys = List.of();
            }

            return new PostDtos.Create(safeTitle, safeContent, boardType, safeImageKeys);
        }
    }

    /**
     * 게시글 수정 요청 DTO
     */
    public record UpdateRequest(
            /**
             * 수정할 게시글 제목
             * 최대 200자까지 허용됩니다.
             */
            @NotBlank(message = "제목을 입력해주세요") @Size(max = 200, message = "제목은 최대 200자까지 입력 가능합니다") String title,

            /**
             * 수정할 게시글 본문 내용
             * 최대 5000자까지 허용됩니다.
             */
            @NotBlank(message = "내용을 입력해주세요") @Size(max = 5000, message = "내용은 최대 5000자까지 입력 가능합니다") String content,

            /**
             * 수정할 게시글에 첨부된 이미지 파일 키 목록
             * 최대 10개까지 허용됩니다.
             * 각 키는 512자를 초과할 수 없고 비어있을 수 없습니다.
             */
            @Size(max = 10, message = "이미지는 최대 10개까지 첨부 가능합니다") List<@NotBlank(message = "이미지 키는 비어있을 수 없습니다") @Size(max = 512, message = "이미지 키는 최대 512자까지 허용됩니다") String> imageKeys) {
        /**
         * 서비스 계층 DTO로 변환
         */
        public PostDtos.Update toServiceDto() {
            // 문자열 입력 정규화 및 방어적 복사
            String safeTitle = title != null ? title.trim() : "";
            String safeContent = content != null ? content.trim() : "";

            // 이미지 키 정규화: 공백 제거, 중복 제거, null 안전
            List<String> safeImageKeys;
            if (imageKeys != null) {
                safeImageKeys = imageKeys.stream()
                        .filter(key -> key != null && !key.isBlank()) // null 또는 빈 문자열 제거
                        .map(String::trim) // 앞뒤 공백 제거
                        .distinct() // 중복 제거
                        .toList(); // 불변 리스트로 변환
            } else {
                safeImageKeys = List.of();
            }

            return new PostDtos.Update(safeTitle, safeContent, safeImageKeys);
        }
    }
}
