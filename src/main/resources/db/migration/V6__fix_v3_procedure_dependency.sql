-- V6__fix_v3_procedure_dependency.sql
-- V3 마이그레이션의 프로시저 의존성을 제거하고 withdrawal_date 컬럼 추가

-- members 테이블에 withdrawal_date 컬럼 추가 (Member 엔티티에 있음)
-- 프로시저 없이 IF NOT EXISTS 안전 실행
-- MySQL 8.0.29+ 이상 버전 호환 버전
SELECT 'Adding withdrawal_date column' AS 'Info';
SELECT COUNT(*) INTO @column_exists FROM information_schema.columns 
  WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'withdrawal_date';

SET @query = IF(@column_exists = 0, 
  'ALTER TABLE members ADD withdrawal_date DATETIME(6) NULL', 
  'SELECT \'withdrawal_date column already exists\' AS \'Info\'');

PREPARE stmt FROM @query;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- active 컬럼이 혹시 누락된 경우를 대비해 동일 방식으로 추가
SELECT 'Adding active column if not exists' AS 'Info';
SELECT COUNT(*) INTO @active_exists FROM information_schema.columns 
  WHERE table_schema = DATABASE() AND table_name = 'members' AND column_name = 'active';

SET @active_query = IF(@active_exists = 0, 
  'ALTER TABLE members ADD active TINYINT(1) NOT NULL DEFAULT 1', 
  'SELECT \'active column already exists\' AS \'Info\'');

PREPARE active_stmt FROM @active_query;
EXECUTE active_stmt;
DEALLOCATE PREPARE active_stmt;

-- 기존 데이터를 활성 상태로 설정
UPDATE members SET active = 1 WHERE active IS NULL;
