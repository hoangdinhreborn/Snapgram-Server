CREATE TABLE chat_conversations (
    id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(150),
    avatar_url VARCHAR(1000),
    created_by UUID NOT NULL,
    vanish_mode_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    vanish_mode_set_by UUID,
    vanish_mode_set_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_chat_conversations_type ON chat_conversations(type);
CREATE INDEX idx_chat_conversations_created_by ON chat_conversations(created_by);

CREATE TABLE chat_conversation_members (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    muted_until TIMESTAMPTZ
);
CREATE INDEX idx_chat_conversation_members_conversation_id ON chat_conversation_members(conversation_id);
CREATE INDEX idx_chat_conversation_members_user_id ON chat_conversation_members(user_id);
CREATE UNIQUE INDEX uk_chat_active_membership ON chat_conversation_members(conversation_id, user_id) WHERE left_at IS NULL;

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,
    content TEXT,
    media_id UUID,
    media_url VARCHAR(1000),
    media_mime_type VARCHAR(150),
    media_size_bytes BIGINT CHECK (media_size_bytes IS NULL OR media_size_bytes >= 0),
    media_duration_sec INT CHECK (media_duration_sec IS NULL OR media_duration_sec >= 0),
    thumbnail_url VARCHAR(1000),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_for_everyone BOOLEAN NOT NULL DEFAULT FALSE,
    reply_to_message_id UUID,
    is_forwarded BOOLEAN NOT NULL DEFAULT FALSE,
    forwarded_from_message_id UUID,
    is_ephemeral BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    edited_at TIMESTAMPTZ
);
CREATE INDEX idx_chat_messages_conversation_id ON chat_messages(conversation_id);
CREATE INDEX idx_chat_messages_sender_id ON chat_messages(sender_id);
CREATE INDEX idx_chat_messages_media_id ON chat_messages(media_id);
CREATE INDEX idx_chat_messages_reply_to ON chat_messages(reply_to_message_id);
CREATE INDEX idx_chat_messages_forwarded_from ON chat_messages(forwarded_from_message_id);
CREATE INDEX idx_chat_messages_expires_at ON chat_messages(expires_at);
CREATE INDEX idx_chat_messages_created_at ON chat_messages(created_at);
CREATE UNIQUE INDEX uk_chat_messages_conversation_id_id ON chat_messages(conversation_id, id);
CREATE INDEX idx_chat_messages_content_search ON chat_messages USING GIN (to_tsvector('simple', coalesce(content, '')));

CREATE TABLE chat_message_reactions (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    emoji VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_chat_message_reactions_message_user UNIQUE (message_id, user_id)
);
ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_reply_same_conversation
    FOREIGN KEY (conversation_id, reply_to_message_id)
    REFERENCES chat_messages(conversation_id, id) ON DELETE SET NULL;

ALTER TABLE chat_messages
    ADD CONSTRAINT fk_chat_messages_forward_same_conversation
    FOREIGN KEY (conversation_id, forwarded_from_message_id)
    REFERENCES chat_messages(conversation_id, id) ON DELETE SET NULL;

CREATE INDEX idx_chat_message_reactions_message_id
 ON chat_message_reactions(message_id);
CREATE INDEX idx_chat_message_reactions_user_id ON chat_message_reactions(user_id);

CREATE TABLE chat_conversation_read_status (
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    last_read_message_id UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chat_conversation_read_status PRIMARY KEY (conversation_id, user_id)
);
ALTER TABLE chat_conversation_read_status
    ADD CONSTRAINT fk_chat_read_status_same_conversation
    FOREIGN KEY (conversation_id, last_read_message_id)
    REFERENCES chat_messages(conversation_id, id) ON DELETE CASCADE;

CREATE INDEX idx_chat_read_status_user_id ON chat_conversation_read_status(user_id);

CREATE TABLE chat_message_hidden (
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    hidden_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chat_message_hidden PRIMARY KEY (message_id, user_id)
);
CREATE INDEX idx_chat_message_hidden_user_id ON chat_message_hidden(user_id);

CREATE TABLE chat_pinned_messages (
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    pinned_by UUID NOT NULL,
    pinned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_chat_pinned_messages PRIMARY KEY (conversation_id, message_id)
);
ALTER TABLE chat_pinned_messages
    ADD CONSTRAINT fk_chat_pinned_same_conversation
    FOREIGN KEY (conversation_id, message_id)
    REFERENCES chat_messages(conversation_id, id) ON DELETE CASCADE;

CREATE INDEX idx_chat_pinned_messages_message_id ON chat_pinned_messages(message_id);

CREATE TABLE chat_call_history (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES chat_conversations(id) ON DELETE CASCADE,
    initiated_by UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ
);
CREATE INDEX idx_chat_call_history_conversation_id ON chat_call_history(conversation_id);
CREATE INDEX idx_chat_call_history_initiated_by ON chat_call_history(initiated_by);
CREATE INDEX idx_chat_call_history_status ON chat_call_history(status);
CREATE INDEX idx_chat_call_history_started_at ON chat_call_history(started_at);
