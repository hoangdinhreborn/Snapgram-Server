-- Media cho bình luận: ảnh / GIF / sticker
ALTER TABLE content_comments
    ADD COLUMN IF NOT EXISTS media_url VARCHAR(1000);

ALTER TABLE content_comments
    ADD COLUMN IF NOT EXISTS media_type VARCHAR(20);

-- Bình luận chỉ có media (không có chữ) là hợp lệ
ALTER TABLE content_comments
    ALTER COLUMN content DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_content_comments_media_type
    ON content_comments(media_type) WHERE media_type IS NOT NULL;
