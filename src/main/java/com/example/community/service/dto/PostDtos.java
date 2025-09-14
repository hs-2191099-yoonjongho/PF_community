package com.example.community.service.dto;

import com.example.community.domain.BoardType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 게시글 생성 및 수정에 사용되는 DTO 클래스
 * 서비스 계층에서 게시글 관련 데이터를 전달하는 데 사용됩니다.
 */
public class PostDtos {
    /**
     * 게시글 생성 요청 DTO
     * 새 게시글을 작성할 때 필요한 데이터를 담습니다.
     */
    public record Create(
            /**
             * 게시글 제목
             * 최대 200자까지 허용됩니다.
             */
            String title,

            /**
             * 게시글 본문 내용
             * 최대 5000자까지 허용됩니다.
             */
            String content,

            /**
             * 게시판 유형 (자유게시판, 공지사항 등)
             */
            BoardType boardType,

            /**
             * 게시글에 첨부된 이미지 파일 키 목록
             * 최대 10개까지 허용됩니다.
             */
            List<String> imageKeys) {
        /**
         * 생성자에서 불변성을 보장합니다.
         */
        public Create {
            // 이미지 키 목록이 null인 경우 빈 리스트로 초기화
            imageKeys = (imageKeys != null)
                    ? Collections.unmodifiableList(new ArrayList<>(imageKeys))
                    : Collections.emptyList();
        }

        /**
         * 자유게시판(FREE) 타입으로 게시글 생성 DTO 생성
         */
        public static Create free(String title, String content) {
            return new Create(title, content, BoardType.FREE, Collections.emptyList());
        }

        /**
         * 공지사항(NOTICE) 타입으로 게시글 생성 DTO 생성
         */
        public static Create notice(String title, String content) {
            return new Create(title, content, BoardType.NOTICE, Collections.emptyList());
        }
    }

    /**
     * 게시글 수정 요청 DTO
     * 기존 게시글을 수정할 때 필요한 데이터를 담습니다.
     */
    public record Update(
            /**
             * 수정할 게시글 제목
             * 최대 200자까지 허용됩니다.
             */
            String title,

            /**
             * 수정할 게시글 본문 내용
             * 최대 5000자까지 허용됩니다.
             */
            String content,

            /**
             * 수정할 게시글에 첨부된 이미지 파일 키 목록
             * 최대 10개까지 허용됩니다.
             */
            List<String> imageKeys) {
        /**
         * 생성자에서 불변성을 보장합니다.
         */
        public Update {
            // 이미지 키 목록이 null인 경우 빈 리스트로 초기화
            imageKeys = (imageKeys != null)
                    ? Collections.unmodifiableList(new ArrayList<>(imageKeys))
                    : Collections.emptyList();
        }
    }
}
