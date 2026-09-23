
CREATE TABLE content_posts (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    caption TEXT,
    media_id UUID,
    media_url VARCHAR(1000),
    tags TEXT,
    like_count BIGINT NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    comment_count BIGINT NOT NULL DEFAULT 0 CHECK (comment_count >= 0),
    view_count BIGINT NOT NULL DEFAULT 0 CHECK (view_count >= 0),
    status VARCHAR(20) NOT NULL,
    edited_at TIMESTAMPTZ,
    edit_count INT NOT NULL DEFAULT 0 CHECK (edit_count >= 0),
    visibility VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_content_posts_author_id ON content_posts(author_id);
CREATE INDEX idx_content_posts_status ON content_posts(status);
CREATE INDEX idx_content_posts_visibility ON content_posts(visibility);
CREATE INDEX idx_content_posts_media_id ON content_posts(media_id);
CREATE INDEX idx_content_posts_created_at ON content_posts(created_at);

CREATE TABLE content_comments (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL,
    content TEXT NOT NULL,
    parent_comment_id UUID,
    like_count BIGINT NOT NULL DEFAULT 0 CHECK (like_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ,
    CONSTRAINT uk_content_comments_post_id_id UNIQUE (post_id, id)
);
CREATE INDEX idx_content_comments_post_id ON content_comments(post_id);
CREATE INDEX idx_content_comments_author_id ON content_comments(author_id);
CREATE INDEX idx_content_comments_parent_id ON content_comments(parent_comment_id);
ALTER TABLE content_comments
    ADD CONSTRAINT fk_content_comments_parent_same_post
    FOREIGN KEY (post_id, parent_comment_id)
    REFERENCES content_comments(post_id, id) ON DELETE CASCADE;

CREATE INDEX idx_content_comments_created_at ON content_comments(created_at);

CREATE TABLE content_comment_likes (
    comment_id UUID NOT NULL REFERENCES content_comments(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_comment_likes PRIMARY KEY (comment_id, user_id)
);
CREATE INDEX idx_content_comment_likes_user_id ON content_comment_likes(user_id);

CREATE TABLE content_interactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    watch_time_ratio DECIMAL(6,5),
    explicit_rating DECIMAL(4,2),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_content_interactions_watch_ratio CHECK (watch_time_ratio IS NULL OR (watch_time_ratio >= 0 AND watch_time_ratio <= 1))
);
CREATE INDEX idx_content_interactions_user_post_type ON content_interactions(user_id, post_id, type);
CREATE INDEX idx_content_interactions_post_id ON content_interactions(post_id);
CREATE INDEX idx_content_interactions_type ON content_interactions(type);
CREATE INDEX idx_content_interactions_created_at ON content_interactions(created_at);
CREATE UNIQUE INDEX uk_content_interactions_like ON content_interactions(user_id, post_id, type) WHERE type = 'LIKE';

CREATE TABLE content_follows (
    id UUID PRIMARY KEY,
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_content_follows_pair UNIQUE (follower_id, following_id),
    CONSTRAINT ck_content_follows_not_self CHECK (follower_id <> following_id)
);
CREATE INDEX idx_content_follows_follower_id ON content_follows(follower_id);
CREATE INDEX idx_content_follows_following_id ON content_follows(following_id);
CREATE INDEX idx_content_follows_status ON content_follows(status);

CREATE TABLE content_user_privacy_view (
    user_id UUID PRIMARY KEY,
    is_private BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE content_saved_posts (
    user_id UUID NOT NULL,
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    collection_id UUID,
    saved_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_saved_posts PRIMARY KEY (user_id, post_id)
);
CREATE INDEX idx_content_saved_posts_post_id ON content_saved_posts(post_id);
CREATE INDEX idx_content_saved_posts_collection_id ON content_saved_posts(collection_id);

CREATE TABLE content_collections (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT TRUE,
    cover_post_id UUID REFERENCES content_posts(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_content_collections_user_id ON content_collections(user_id);
ALTER TABLE content_saved_posts
    ADD CONSTRAINT fk_content_saved_posts_collection
    FOREIGN KEY (collection_id) REFERENCES content_collections(id) ON DELETE SET NULL;

CREATE TABLE content_reports (
    id UUID PRIMARY KEY,
    reporter_id UUID NOT NULL,
    target_type VARCHAR(30) NOT NULL,
    target_id UUID NOT NULL,
    reason VARCHAR(100) NOT NULL,
    description TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_content_reports_reporter_id ON content_reports(reporter_id);
CREATE INDEX idx_content_reports_target ON content_reports(target_type, target_id);
CREATE INDEX idx_content_reports_status ON content_reports(status);

CREATE TABLE content_mutes (
    id UUID PRIMARY KEY,
    muter_id UUID NOT NULL,
    muted_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_content_mutes_pair UNIQUE (muter_id, muted_id),
    CONSTRAINT ck_content_mutes_not_self CHECK (muter_id <> muted_id)
);
CREATE INDEX idx_content_mutes_muter_id ON content_mutes(muter_id);
CREATE INDEX idx_content_mutes_muted_id ON content_mutes(muted_id);

CREATE TABLE content_stories (
    id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    media_id UUID,
    media_url VARCHAR(1000) NOT NULL,
    media_type VARCHAR(30) NOT NULL,
    caption TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    visibility VARCHAR(30) NOT NULL
);
CREATE INDEX idx_content_stories_author_id ON content_stories(author_id);
CREATE INDEX idx_content_stories_media_id ON content_stories(media_id);
CREATE INDEX idx_content_stories_expires_at ON content_stories(expires_at);
CREATE INDEX idx_content_stories_visibility ON content_stories(visibility);

CREATE TABLE content_story_views (
    story_id UUID NOT NULL REFERENCES content_stories(id) ON DELETE CASCADE,
    viewer_id UUID NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_story_views PRIMARY KEY (story_id, viewer_id)
);
CREATE INDEX idx_content_story_views_viewer_id ON content_story_views(viewer_id);

CREATE TABLE content_highlights (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    cover_story_id UUID REFERENCES content_stories(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_content_highlights_user_id ON content_highlights(user_id);
CREATE INDEX idx_content_highlights_cover_story_id ON content_highlights(cover_story_id);

CREATE TABLE content_highlight_items (
    highlight_id UUID NOT NULL REFERENCES content_highlights(id) ON DELETE CASCADE,
    story_id UUID NOT NULL REFERENCES content_stories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_highlight_items PRIMARY KEY (highlight_id, story_id)
);
CREATE INDEX idx_content_highlight_items_story_id ON content_highlight_items(story_id);

CREATE TABLE content_hashtags (
    id UUID PRIMARY KEY,
    tag VARCHAR(100) NOT NULL UNIQUE,
    post_count BIGINT NOT NULL DEFAULT 0 CHECK (post_count >= 0),
    last_used_at TIMESTAMPTZ
);

CREATE TABLE content_post_hashtags (
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    hashtag_id UUID NOT NULL REFERENCES content_hashtags(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_post_hashtags PRIMARY KEY (post_id, hashtag_id)
);
CREATE INDEX idx_content_post_hashtags_hashtag_id ON content_post_hashtags(hashtag_id);

CREATE TABLE content_close_friends (
    owner_id UUID NOT NULL,
    friend_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_close_friends PRIMARY KEY (owner_id, friend_id),
    CONSTRAINT ck_content_close_friends_not_self CHECK (owner_id <> friend_id)
);
CREATE INDEX idx_content_close_friends_friend_id ON content_close_friends(friend_id);

CREATE TABLE content_post_mentions (
    post_id UUID NOT NULL REFERENCES content_posts(id) ON DELETE CASCADE,
    mentioned_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_content_post_mentions PRIMARY KEY (post_id, mentioned_user_id)
);
CREATE INDEX idx_content_post_mentions_user_id ON content_post_mentions(mentioned_user_id);

ALTER TABLE content_posts
    ADD CONSTRAINT ck_content_posts_counters_nonnegative CHECK (like_count >= 0 AND comment_count >= 0 AND view_count >= 0);
ALTER TABLE content_comments
    ADD CONSTRAINT ck_content_comments_root_or_reply CHECK (parent_comment_id IS NULL OR parent_comment_id <> id);
