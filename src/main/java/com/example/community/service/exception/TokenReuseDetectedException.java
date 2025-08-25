package com.example.community.service.exception;

/**
 * 토큰 재사용 탐지 시 발생하는 예외
 */
public class TokenReuseDetectedException extends RuntimeException {
    
    public TokenReuseDetectedException() {
        super("토큰 재사용이 탐지되었습니다. 보안을 위해 모든 토큰이 폐기됩니다.");
    }
    
    public TokenReuseDetectedException(String message) {
        super(message);
    }
}
