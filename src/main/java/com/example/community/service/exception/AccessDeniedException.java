package com.example.community.service.exception;

/**
 * 접근 권한이 없는 경우 발생하는 예외
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
    
    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
