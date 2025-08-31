-- V7__add_refresh_tokens.sql
-- RefreshToken 엔티티를 위한 refresh_tokens 테이블 생성

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash VARCHAR(44) NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NULL,
    
    -- 외래키 제약조건
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES members (id) ON DELETE CASCADE,
    
    -- 유니크 제약조건
    CONSTRAINT uk_token_hash UNIQUE (token_hash),
    
    -- 인덱스
    INDEX idx_refresh_token_user_revoked_id (user_id, revoked, id),
    INDEX idx_refresh_token_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
