package com.example.community.service.exception;

/**
 * 이미지 처리 관련 예외
 */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
