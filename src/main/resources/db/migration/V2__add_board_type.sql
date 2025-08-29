-- V2__add_board_type.sql

-- posts 테이블에 board_type 컬럼 추가
ALTER TABLE posts ADD COLUMN board_type VARCHAR(20) NOT NULL DEFAULT 'FREE';

-- 기존 데이터를 자유게시판으로 설정
UPDATE posts SET board_type = 'FREE' WHERE board_type IS NULL;

-- board_type 단일 인덱스 추가
CREATE INDEX idx_post_board_type ON posts (board_type);

-- board_type + created_at 복합 인덱스 추가 (최신 공지사항, 최신 자유게시판 등 조회 성능 향상)
CREATE INDEX idx_post_board_type_created_at ON posts (board_type, created_at DESC);