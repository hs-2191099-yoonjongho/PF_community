-- V3__add_member_active_status.sql
-- members 테이블에 활성 상태 컬럼 추가 (존재 여부 체크)
DELIMITER $$
CREATE PROCEDURE add_active_column_if_missing()
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
		WHERE TABLE_SCHEMA = DATABASE()
		  AND TABLE_NAME = 'members'
		  AND COLUMN_NAME = 'active'
	) THEN
		ALTER TABLE members ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1;
	END IF;
END$$
DELIMITER ;

CALL add_active_column_if_missing();
DROP PROCEDURE add_active_column_if_missing;

