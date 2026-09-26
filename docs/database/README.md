# Snapgram Database Design

Source of truth: `../../snapgram.dbml` (dbdiagram.io / DBML v2.1).

## Rules

- One PostgreSQL database per service.
- PostgreSQL foreign keys are used only inside the same database.
- Cross-service IDs are UUID logical references; consistency is propagated via REST/Kafka.
- `MUTUAL` follow status is derived from two accepted rows, never stored.
- Comment replies use `parent_comment_id` with a maximum depth of one.
- `auth_blocks` is the source of truth for blocking; `content_mutes` is the source of truth for mute.
- `recommender-service` stores CQRS read models and does not own content posts.
- Chatbot DB is intentionally unmodeled beyond a Flyway placeholder until its original schema is confirmed.

## Flyway baseline

Each DB-backed service keeps migrations under:

`src/main/resources/db/migration/`

Current baseline is `V1`:

- `auth-service`: users, roles, refresh tokens, blocks, notes, user search indexes
- `media-service`: media files + metadata
- `content-service`: posts, comments/replies, likes, interactions, follows, saves/collections, moderation, stories/highlights, hashtags, close friends, mentions
- `chat-service`: conversations/members/messages, message actions, reactions, read state, hidden messages, pins, calls
- `chatbot-service`: placeholder only; schema not specified by the source plan
- `recommender-service`: CQRS post/interaction/follow/block/mute read models
- `notification-service`: notifications + device tokens

Subsequent migrations `V2+`:

- `auth-service`: `V2__add_email_verification_and_password_reset.sql` (email verification, password reset tokens)
- `content-service`: `V2__create_post_media.sql` (supports multiple photos/videos per post — Carousel/Album)

## Important constraint rules

- Comment parent reference is constrained to the same post.
- Follow/mute/block relationships disallow self-targeting.
- Like/save/reaction relationships are idempotent through unique constraints/indexes.
- Chat active membership uses a partial unique index on `(conversation_id, user_id)` where `left_at IS NULL`.
- Chat reply/forward/read/pin references are constrained to the same conversation where the schema permits it.
- Cross-service references such as `author_id`, `user_id`, `media_id`, and `post_id` are intentionally not SQL foreign keys.
