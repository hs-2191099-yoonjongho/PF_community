-- test-data-ci.sql (CI용 테스트 데이터 - 현재 스키마와 1:1 매칭)
-- CI/자동화 테스트용으로 최소한의 데이터만 포함 (현행 스키마와 완벽 호환)
USE board;

-- 1. 테스트 회원 생성
INSERT INTO members (username, email, password, active, created_at, updated_at) VALUES
('testuser1', 'test1@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', 1, NOW(), NOW()),
('testuser2', 'test2@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', 1, NOW(), NOW()),
('testuser3', 'test3@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', 1, NOW(), NOW());

-- 2. 테스트 게시글 생성 (V5 마이그레이션 적용 전이라면 author_id 대신 member_id 사용)
INSERT INTO posts (title, content, author_id, view_count, like_count, version, created_at, updated_at) VALUES
('테스트 게시글 1', '테스트 내용입니다', 1, 10, 5, 0, NOW(), NOW()),
('테스트 게시글 2', '두 번째 게시글입니다', 2, 20, 10, 0, NOW(), NOW()),
('테스트 게시글 3', '세 번째 게시글입니다', 3, 30, 15, 0, NOW(), NOW());

-- 3. 회원 역할 추가 (V5 마이그레이션으로 테이블 생성 필요)
INSERT INTO member_roles (member_id, role) VALUES
(1, 'ROLE_USER'),
(2, 'ROLE_USER'), 
(3, 'ROLE_USER');
