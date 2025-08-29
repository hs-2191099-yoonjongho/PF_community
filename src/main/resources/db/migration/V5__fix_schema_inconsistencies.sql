-- V5__fix_schema_inconsistencies.sql
-- 스키마와 엔티티 클래스 간의 불일치 문제 해결

-- 1. posts 테이블: member_id를 author_id로 변경 (Member 엔티티 필드명과 일치)
-- 먼저 기존 FK와 인덱스 제거
ALTER TABLE posts DROP FOREIGN KEY fk_posts_member;
ALTER TABLE posts DROP INDEX idx_posts_member;

-- member_id를 author_id로 변경
ALTER TABLE posts CHANGE member_id author_id BIGINT NOT NULL;

-- FK와 인덱스 재생성
ALTER TABLE posts ADD CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES members (id) ON DELETE CASCADE;
CREATE INDEX idx_posts_author ON posts (author_id);

-- 2. comments 테이블에서도 member_id를 author_id로 변경
-- 먼저 기존 FK와 인덱스 제거
ALTER TABLE comments DROP FOREIGN KEY fk_comments_member;
ALTER TABLE comments DROP INDEX idx_comments_member;

-- member_id를 author_id로 변경
ALTER TABLE comments CHANGE member_id author_id BIGINT NOT NULL;

-- FK와 인덱스 재생성
ALTER TABLE comments ADD CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES members (id) ON DELETE CASCADE;
CREATE INDEX idx_comments_author ON comments (author_id);

-- 3. posts 테이블: 누락된 컬럼 추가 (Post 엔티티에 있지만 스키마에 없는 컬럼들)
ALTER TABLE posts ADD view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD like_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD version BIGINT NULL DEFAULT 0;

-- 4. members 테이블: nickname을 username으로 변경 (Member 엔티티 필드명과 일치)
ALTER TABLE members CHANGE nickname username VARCHAR(50) NOT NULL;

-- 5. member_roles 테이블 생성 (Member 엔티티의 roles 컬렉션과 매핑)
CREATE TABLE member_roles (
    member_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    PRIMARY KEY (member_id, role),
    CONSTRAINT fk_member_roles_member FOREIGN KEY (member_id) REFERENCES members (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 6. 성능 최적화를 위한 인덱스 추가 (DESC 제거, 일반 인덱스로 변경)
CREATE INDEX idx_posts_like_count ON posts (like_count);
-- member_roles는 이미 PK 인덱스가 있어 추가 인덱스 제거
