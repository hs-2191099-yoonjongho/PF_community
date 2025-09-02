-- 회원 테이블에 토큰 버전 컬럼 추가
-- 모든 기존 회원의 토큰 버전을 0으로 설정

ALTER TABLE members
ADD token_version INT NOT NULL DEFAULT 0;

-- 인덱스 추가 (토큰 버전 조회 최적화)
CREATE INDEX idx_member_token_version ON members(token_version);

