-- V8__add_updated_at_to_post_images.sql
-- PostImage 엔티티와 post_images 테이블 사이의 불일치 해결

-- post_images 테이블에 updated_at 컬럼 추가
ALTER TABLE post_images ADD updated_at DATETIME(6) NULL;

-- 기존 레코드의 updated_at 값을 created_at과 동일하게 설정
UPDATE post_images SET updated_at = created_at WHERE updated_at IS NULL;
