-- V15__add_username_unique_index.sql
-- members.username에 유니크 인덱스 추가 (중복 방지)

ALTER TABLE members
ADD CONSTRAINT uk_member_username UNIQUE (username);
