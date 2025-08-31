-- V9__add_updated_at_to_comments.sql
-- 댓글 테이블에 updated_at 컬럼 추가

-- comments 테이블에 updated_at 컬럼 추가
ALTER TABLE comments ADD updated_at DATETIME(6) NULL;

-- 기존 레코드의 updated_at 값을 created_at과 동일하게 설정
UPDATE comments SET updated_at = created_at WHERE updated_at IS NULL;
