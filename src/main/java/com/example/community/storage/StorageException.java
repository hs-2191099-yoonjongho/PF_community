package com.example.community.storage;

/**
 * 파일 저장소 관련 예외 클래스
 * 파일 저장, 삭제 등 스토리지 작업 중 발생하는 모든 예외를 래핑합니다.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
