CREATE TABLE content_post_media (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    media_id UUID,
    media_url VARCHAR(1000) NOT NULL,
    thumbnail_url VARCHAR(1000),
    media_type VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    width INT CHECK (width IS NULL OR width >= 0),
    height INT CHECK (height IS NULL OR height >= 0),
    duration_sec INT CHECK (duration_sec IS NULL OR duration_sec >= 0),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_content_post_media_post_id ON content_post_media(post_id);
CREATE INDEX idx_content_post_media_post_order ON content_post_media(post_id, sort_order);
CREATE UNIQUE INDEX uk_content_post_media_order ON content_post_media(post_id, sort_order);

-- Migrate existing posts with media_url to have 1 item in content_post_media
INSERT INTO content_post_media (id, post_id, media_id, media_url, media_type, sort_order, created_at)
SELECT gen_random_uuid(), id, media_id, media_url,
       CASE WHEN content_type = 'VIDEO' THEN 'VIDEO' ELSE 'IMAGE' END,
       0, created_at
FROM content_posts
WHERE media_url IS NOT NULL;
