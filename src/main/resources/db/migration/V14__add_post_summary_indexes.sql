-- 게시글 성능 최적화 인덱스 추가 (created_at+id, like_count+created_at+id, board_type+created_at+id)
CREATE INDEX idx_post_created_id ON posts (created_at DESC, id DESC);
CREATE INDEX idx_post_like_created_id ON posts (like_count DESC, created_at DESC, id DESC);
CREATE INDEX idx_post_boardtype_created_id ON posts (board_type, created_at DESC, id DESC);
