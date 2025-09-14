package com.example.community.service.exception;

/**
 * 비즈니스 권한 거부(Forbidden) 예외 - 서비스 계층 전용
 */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }

    public ForbiddenOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
