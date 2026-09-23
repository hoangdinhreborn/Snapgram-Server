CREATE TABLE media_files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL UNIQUE,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    url VARCHAR(1000),
    thumbnail_url VARCHAR(1000),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_media_files_owner_id ON media_files(owner_id);
CREATE INDEX idx_media_files_status ON media_files(status);
CREATE INDEX idx_media_files_created_at ON media_files(created_at);

CREATE TABLE media_metadata (
    media_id UUID PRIMARY KEY REFERENCES media_files(id) ON DELETE CASCADE,
    width INT CHECK (width IS NULL OR width >= 0),
    height INT CHECK (height IS NULL OR height >= 0),
    duration_sec INT CHECK (duration_sec IS NULL OR duration_sec >= 0),
    exif_stripped BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB
);
