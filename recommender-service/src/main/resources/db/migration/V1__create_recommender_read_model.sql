CREATE TABLE recommender_post_view (
    post_id UUID PRIMARY KEY,
    author_id UUID NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    caption TEXT,
    tags TEXT,
    visibility VARCHAR(30) NOT NULL,
    author_is_private BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_recommender_post_view_author_id ON recommender_post_view(author_id);
CREATE INDEX idx_recommender_post_view_visibility ON recommender_post_view(visibility);
CREATE INDEX idx_recommender_post_view_created_at ON recommender_post_view(created_at);

CREATE TABLE recommender_interaction_view (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    post_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    watch_time_ratio DECIMAL(6,5),
    explicit_rating DECIMAL(4,2),
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_recommender_interaction_user_created ON recommender_interaction_view(user_id, created_at);
CREATE INDEX idx_recommender_interaction_post_id ON recommender_interaction_view(post_id);
CREATE INDEX idx_recommender_interaction_type ON recommender_interaction_view(type);

CREATE TABLE recommender_follow_view (
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_recommender_follow_view PRIMARY KEY (follower_id, following_id),
    CONSTRAINT ck_recommender_follow_view_not_self CHECK (follower_id <> following_id)
);
CREATE INDEX idx_recommender_follow_view_following_id ON recommender_follow_view(following_id);
CREATE INDEX idx_recommender_follow_view_status ON recommender_follow_view(status);

CREATE TABLE recommender_block_view (
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_recommender_block_view PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT ck_recommender_block_view_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_recommender_block_view_blocked_id ON recommender_block_view(blocked_id);

CREATE TABLE recommender_mute_view (
    muter_id UUID NOT NULL,
    muted_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_recommender_mute_view PRIMARY KEY (muter_id, muted_id),
    CONSTRAINT ck_recommender_mute_view_not_self CHECK (muter_id <> muted_id)
);
CREATE INDEX idx_recommender_mute_view_muted_id ON recommender_mute_view(muted_id);
