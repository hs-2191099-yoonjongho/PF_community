package com.example.community.service.exception;

/**
 * 이미 폐기된 토큰이 재사용되었을 때 발생하는 예외
 * 토큰 탈취 및 재사용 공격을 감지하기 위해 사용됩니다.
 */
public class TokenReuseDetectedException extends RuntimeException {

    public TokenReuseDetectedException() {
        super("이미 폐기된 토큰이 재사용되었습니다. 보안 위협이 감지되었습니다.");
    }

    public TokenReuseDetectedException(String message) {
        super(message);
    }
}
