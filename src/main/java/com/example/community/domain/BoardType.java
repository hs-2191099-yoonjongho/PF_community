package com.example.community.domain;

/**
 * 게시판 유형을 정의하는 열거형
 * FREE: 자유게시판
 * NOTICE: 공지사항
 */
public enum BoardType {
    FREE("자유게시판"),
    NOTICE("공지사항");

    private final String description;

    BoardType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
