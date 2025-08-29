-- 테스트용 데이터 생성
USE board;

-- 1. 테스트 회원 생성
INSERT INTO members (username, email, password, created_at, updated_at) VALUES
('testuser1', 'test1@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', NOW(), NOW()),
('testuser2', 'test2@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', NOW(), NOW()),
('testuser3', 'test3@example.com', '$2a$10$abcdefghijklmnopqrstuvwxyz', NOW(), NOW());

-- 2. 테스트 게시글 생성 (다양한 추천수로)
INSERT INTO posts (title, content, author_id, view_count, like_count, version, created_at, updated_at) VALUES
('인기 게시글 1', '이 글은 추천수가 50개입니다', 1, 150, 50, 0, NOW(), NOW()),
('베스트 게시글', '이 글은 추천수가 120개입니다', 2, 500, 120, 0, NOW(), NOW()),
('일반 게시글 1', '이 글은 추천수가 5개입니다', 1, 25, 5, 0, NOW(), NOW()),
('일반 게시글 2', '이 글은 추천수가 15개입니다', 3, 75, 15, 0, NOW(), NOW()),
('인기 게시글 2', '이 글은 추천수가 85개입니다', 2, 300, 85, 0, NOW(), NOW()),
('최신 게시글', '이 글은 추천수가 2개입니다', 3, 10, 2, 0, NOW(), NOW());

-- 3. 회원 역할 추가 (필요한 경우)
INSERT INTO member_roles (member_id, role) VALUES
(1, 'ROLE_USER'),
(2, 'ROLE_USER'), 
(3, 'ROLE_USER');
