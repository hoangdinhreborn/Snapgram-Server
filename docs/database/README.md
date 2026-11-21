# Snapgram Database Design

Source of truth: `../../snapgram.dbml` (dbdiagram.io / DBML v3.0).

## Rules

- One PostgreSQL database per service (Seven microservice databases).
- PostgreSQL foreign keys are used only inside the same database.
- Cross-service IDs are UUID logical references; consistency is propagated via REST/Kafka.
- `MUTUAL` follow status is derived from two accepted rows, never stored.
- Comment replies use `parent_comment_id` with a maximum depth of one.
- `auth_blocks` is the source of truth for blocking; `content_mutes` is the source of truth for mute.
- `recommender-service` stores CQRS read models and does not own content posts.
- Transactional Outbox pattern is implemented in `payment-service` (`payment_outbox`) and `notification-service` (`notification_push_outbox`).
- Idempotent event consumers use dedicated processed events tables (`auth_payment_processed_events`, `notification_processed_events`, `payment_event_log`).

## Flyway baseline & Migrations

Each DB-backed service keeps migrations under:

`src/main/resources/db/migration/`

Summary of schemas across services:

1. **`auth-service`**:
   - `V1`: `auth_users`, `auth_user_roles`, `auth_refresh_tokens`, `auth_blocks`, `auth_notes`
   - `V2`: `auth_email_verifications`, `auth_password_resets`
   - `V3`: Admin ban moderation fields (`is_banned`, `banned_at`, `ban_reason`)
   - `V4`: Role enum & account lockout (`failed_login_attempts`, `locked_until`)
   - `V5`: Dynamic RBAC roles & permissions (`auth_roles`, `auth_permissions`, `auth_role_permissions`)
   - `V6`: Blue tick verification fields (`is_verified`, `verified_until`) & `auth_payment_processed_events`
   - `V7`: OAuth2 external accounts (`auth_oauth_accounts`)
   - `V8`: Payment management seed permissions (`PAYMENT:READ`, `PAYMENT:RECONCILE`)

2. **`media-service`**:
   - `V1`: `media_files` (S3 object storage metadata) & `media_metadata` (dimensions, duration, EXIF)

3. **`content-service`**:
   - `V1`: Posts, comments, likes, interactions, follows, privacy view, saved posts, collections, reports, mutes, stories, story views, highlights, hashtags, close friends, post mentions
   - `V2`: `content_post_media` (multi-media carousel / album posts)
   - `V3`: Comment media support (`media_url`, `media_type`, content nullable)
   - `V4`: `support_tickets` (user support tickets and admin resolution)

4. **`chat-service`**:
   - `V1`: `chat_conversations`, `chat_conversation_members`, `chat_messages`, `chat_message_reactions`, `chat_conversation_read_status`, `chat_message_hidden`, `chat_pinned_messages`, `chat_call_history`

5. **`recommender-service`**:
   - `V1`: CQRS read models (`recommender_post_view`, `recommender_interaction_view`, `recommender_follow_view`, `recommender_block_view`, `recommender_mute_view`)
   - `V2`: Spring Batch framework schema (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`, `BATCH_STEP_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT`, `BATCH_JOB_EXECUTION_CONTEXT`)
   - `V3`: Recommendation batch job results (`recommender_trending_post`, `recommender_user_recommendation`)
   - `V4`: Unique constraints for batch idempotency (`uq_recommender_trending_post_id`, `uq_recommender_user_rec_pair`)

6. **`payment-service`**:
   - `V1`: `payment_orders` (order tracking, gateway callbacks) & `payment_event_log` (IPN idempotency)
   - `V2`: Provider-agnostic column normalization (MoMo & VNPay support)
   - `V3`: `payment_outbox` (transactional outbox for `payment.completed` event dispatch)

7. **`notification-service`**:
   - `V1`: Base notifications & device tokens
   - `V2`: Schema standardization (`notifications`, `device_tokens`)
   - `V3`: `notification_processed_events` (Kafka consumer idempotency) & `notification_push_outbox` (push dispatch outbox)

## Important constraint rules

- Comment parent reference is constrained to the same post.
- Follow/mute/block relationships disallow self-targeting.
- Like/save/reaction relationships are idempotent through unique constraints/indexes.
- Chat active membership uses a partial unique index on `(conversation_id, user_id)` where `left_at IS NULL`.
- Chat reply/forward/read/pin references are constrained to the same conversation where the schema permits it.
- Cross-service references such as `author_id`, `user_id`, `media_id`, and `post_id` are intentionally not SQL foreign keys.
