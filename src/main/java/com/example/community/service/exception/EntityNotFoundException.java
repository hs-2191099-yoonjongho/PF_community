package com.example.community.service.exception;

/**
 * 엔티티를 찾을 수 없을 때 발생하는 예외
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String entityName, Object id) {
        super(String.format("%s를 찾을 수 없습니다. ID: %s", entityName, id));
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
