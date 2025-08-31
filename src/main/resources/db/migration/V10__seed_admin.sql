-- V10__seed_admin.sql
-- 운영/스테이징 환경에서 ADMIN 계정을 안전하게 시드하기 위한 마이그레이션
-- 환경 변수 -> Spring -> Flyway placeholders 로 주입된 값을 사용합니다.
-- 필요 플레이스홀더
--   ${ADMIN_EMAIL}
--   ${ADMIN_USERNAME}
--   ${ADMIN_PASSWORD_HASH}  -- BCrypt 해시 (예: $2a$12$...)

-- 1) 플레이스홀더가 비어있지 않고, 동일 email 사용자가 없을 때만 멤버 생성
INSERT INTO members (email, password, username, created_at, updated_at, active)
SELECT vals.e, vals.p, vals.u, NOW(6), NULL, 1
FROM (
    SELECT '${ADMIN_EMAIL}' AS e, '${ADMIN_USERNAME}' AS u, '${ADMIN_PASSWORD_HASH}' AS p
) AS vals
WHERE vals.e <> '' AND vals.u <> '' AND vals.p <> ''
  AND NOT EXISTS (
      SELECT 1 FROM members m WHERE m.email = vals.e
  );

-- 2) 방금 생성했거나 기존에 있던 동일 email 사용자에게 ROLE_ADMIN 부여 (없을 때만)
INSERT INTO member_roles (member_id, role)
SELECT m.id, 'ROLE_ADMIN'
FROM members m
JOIN (SELECT '${ADMIN_EMAIL}' AS e) v ON v.e <> ''
WHERE m.email = v.e
  AND NOT EXISTS (
      SELECT 1 FROM member_roles mr WHERE mr.member_id = m.id AND mr.role = 'ROLE_ADMIN'
  );

-- 3) ADMIN 계정은 보통 USER 권한도 함께 부여
INSERT INTO member_roles (member_id, role)
SELECT m.id, 'ROLE_USER'
FROM members m
JOIN (SELECT '${ADMIN_EMAIL}' AS e) v ON v.e <> ''
WHERE m.email = v.e
  AND NOT EXISTS (
      SELECT 1 FROM member_roles mr WHERE mr.member_id = m.id AND mr.role = 'ROLE_USER'
  );

-- 참고:
--  - 플레이스홀더가 비어있으면 어떤 INSERT도 수행되지 않습니다.
--  - 이미 동일 email이 존재하면 신규 members INSERT는 생략되고, 권한만 보강됩니다.
