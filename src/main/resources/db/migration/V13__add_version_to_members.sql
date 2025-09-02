-- 낙관적 잠금을 위한 version 컬럼 추가
ALTER TABLE members
ADD version BIGINT DEFAULT 0 NOT NULL;
