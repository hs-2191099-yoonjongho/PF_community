-- 포스트 이미지를 저장할 테이블 생성
CREATE TABLE post_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    file_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    url VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    
    CONSTRAINT fk_post_images_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE,
    INDEX idx_post_images_post (post_id)
);
