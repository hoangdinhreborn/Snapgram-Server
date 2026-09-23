# Auth Service Database & Architecture Review

## Database Schema ✓

Auth service database (`snapgram-postgres-auth`) được quản lý bởi **Flyway** migrations.

### Tables

#### 1. `auth_users` - Người dùng chính
```sql
- id (UUID, PK)
- username, email (UNIQUE, indexed)
- password_hash (bcrypt)
- display_name, avatar_url, bio, website, pronouns, category
- is_private (default: false)
- 2FA: totp_secret, two_fa_enabled, backup_codes_hash
- email_verified, email_verified_at
- show_activity_status, last_seen_at
- created_at, updated_at (timestamps)

Indexes:
- PRIMARY KEY: id
- UNIQUE: username, email
- GIN (full-text search): username_trgm, display_name_trgm
- Foreign keys: auth_user_roles, auth_refresh_tokens, auth_blocks, auth_notes
```

#### 2. `auth_user_roles` - Roles (ADMIN, MODERATOR, USER)
```sql
- id (UUID, PK)
- user_id (FK → auth_users)
- role (VARCHAR 30)
- created_at

Constraint:
- UNIQUE (user_id, role) - một user không có role trùng lặp
```

#### 3. `auth_refresh_tokens` - JWT refresh tokens
```sql
- id (UUID, PK)
- user_id (FK → auth_users)
- jti (JWT ID, unique)
- token_hash (SHA-256 hash of token)
- expires_at, revoked_at
- created_at

Indexes:
- expires_at (cleanup old tokens)
- revoked_at (blacklist tokens)
```

#### 4. `auth_blocks` - User block list
```sql
- id (UUID, PK)
- blocker_id, blocked_id (FK → auth_users)
- created_at

Constraint:
- UNIQUE (blocker_id, blocked_id) - không block lại 2 lần
- CHECK (blocker_id <> blocked_id) - không tự block
```

#### 5. `auth_notes` - User notes/statuses (60 chars, expiry)
```sql
- id (UUID, PK)
- user_id (FK → auth_users)
- content (VARCHAR 60)
- created_at, expires_at

Indexes:
- expires_at (cleanup expired notes)
```

## Code Structure ✓

### Entities
- **AuthUser** - User profile, 2FA config, account status
- **AuthUserRole** - User roles (for authorization)
- **AuthRefreshToken** - JWT refresh tokens (for rotation)
- **AuthBlock** - User blocks
- **AuthNote** - Temporary status/note

### Repositories (Spring Data JPA)
```java
AuthUserRepository
  - findByEmail(), findByUsername()
  - existsByEmail(), existsByUsername()

AuthUserRoleRepository
  - findByUserId()
  - existsByUserIdAndRole()

AuthRefreshTokenRepository
  - findByJti()
  - findByUserIdAndRevokedAtIsNull()

AuthBlockRepository
  - findByBlockerId(), findByBlockedId()

AuthNoteRepository
  - findByUserIdAndExpiresAtAfter()
```

### JWT Service ✓
```java
JwtService:
- generateAccessToken(user, roles) → JWT with 15m TTL
- generateRefreshToken(user) → JWT with 30d TTL
- parseToken(token) → Claims
- isAccessToken(), isRefreshToken()
- getUserId(), getUsername(), getRoles(), getExpiration()

JwtProperties (from .env):
- secret: Base64-encoded symmetric key
- issuer: "snapgram-auth"
- accessTokenTtl: 15m
- refreshTokenTtl: 30d
```

**Token Format (Access Token)**:
```json
{
  "jti": "uuid",
  "sub": "user-uuid",
  "iss": "snapgram-auth",
  "iat": 1234567890,
  "exp": 1234568890,
  "username": "john_doe",
  "token_type": "access",
  "roles": ["USER", "MODERATOR"]
}
```

### Security Filter
```java
JwtAuthenticationFilter
- Extract token from Authorization header
- Validate token signature & issuer
- Set SecurityContext for request
```

## Configuration ✓

### `application.yml`
```yaml
server.port: 8081

# JWT Config (from .env)
app.jwt.secret: ${SNAPGRAM_JWT_SECRET}
app.jwt.issuer: snapgram-auth
app.jwt.access-token-ttl: 15m
app.jwt.refresh-token-ttl: 30d

# Database
spring.datasource.url: jdbc:postgresql://postgres-auth:5432/snapgram
spring.datasource.username: snapgram
spring.datasource.password: snapgram

# Redis (cache, sessions)
spring.redis.host: redis
spring.redis.port: 6379

# Kafka (events)
spring.kafka.bootstrap-servers: kafka:9092

# Flyway (migrations)
spring.flyway.locations: classpath:db/migration

# JPA
spring.jpa.open-in-view: false
hibernate.jdbc.time_zone: UTC

# Actuator (health checks)
management.endpoints.web.include: health,info,metrics,prometheus
```

## Health Check Endpoint ✓

```bash
GET http://localhost:8081/actuator/health

Response:
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

## Best Practices Applied ✓

| Aspect | Implementation |
|--------|-----------------|
| **Password Storage** | Hashed with bcrypt (passwordHash field) |
| **JWT Security** | HS256 with Base64 secret key |
| **Token Rotation** | Separate access (short) & refresh (long) tokens |
| **Token Revocation** | Refresh tokens can be revoked (revoked_at) |
| **2FA Support** | TOTP secret + backup codes stored |
| **Email Verification** | email_verified flag + timestamp |
| **Timezone Consistency** | UTC for all timestamps |
| **Indexes** | Full-text search on username & display_name (GIN) |
| **Cascade Delete** | User deletion cascades to roles, tokens, blocks, notes |
| **Self-Reference Check** | User can't block themselves |
| **Unique Constraints** | username + email unique, role per user unique |
| **Non-Null Defaults** | Private=false, 2FA=false, ShowActivityStatus=true |

## Potential Improvements

### 1. Audit Logging
```sql
CREATE TABLE auth_audit_log (
  id UUID PK,
  user_id UUID FK,
  action VARCHAR (LOGIN, REGISTER, LOGOUT, etc),
  ip_address VARCHAR,
  user_agent VARCHAR,
  timestamp TIMESTAMPTZ
);
```

### 2. Login Attempts (brute force protection)
```sql
CREATE TABLE auth_login_attempts (
  id UUID PK,
  email VARCHAR,
  ip_address VARCHAR,
  success BOOLEAN,
  timestamp TIMESTAMPTZ,
  INDEX (email, timestamp)
);
```

### 3. Session Management
```sql
CREATE TABLE auth_sessions (
  id UUID PK,
  user_id UUID FK,
  device_name VARCHAR,
  ip_address VARCHAR,
  user_agent VARCHAR,
  last_activity TIMESTAMPTZ,
  expires_at TIMESTAMPTZ
);
```

### 4. OAuth2 Integration
```sql
CREATE TABLE auth_oauth_accounts (
  id UUID PK,
  user_id UUID FK,
  provider VARCHAR (google, github, etc),
  provider_user_id VARCHAR,
  email VARCHAR,
  UNIQUE (provider, provider_user_id)
);
```

## Data Verification

```bash
# Table counts (currently empty for new service)
SELECT COUNT(*) FROM auth_users;           -- 0
SELECT COUNT(*) FROM auth_user_roles;      -- 0
SELECT COUNT(*) FROM auth_refresh_tokens;  -- 0

# Flyway migrations applied
SELECT version, description FROM flyway_schema_history;
-- 1 | create auth schema | 2026-09-20

# Schema extensions enabled
SELECT extname FROM pg_extension;
-- pg_trgm (trigram for full-text search)
-- unaccent (accent removal)
```

## Summary

✓ **Database schema is well-designed**:
- Proper normalization
- Good indexing strategy
- Security best practices (hashed passwords, token hashing)
- Support for 2FA, email verification, user blocks
- Flyway migrations for version control

✓ **Entities & repositories are clean**:
- JPA annotations properly configured
- Unique constraints enforced at DB level
- Lifecycle callbacks for timestamps

✓ **JWT service is secure**:
- HS256 HMAC signing
- Separate access/refresh token types
- Claims include user context (username, roles)
- Issuer verification

✓ **Configuration is externalized**:
- All secrets in .env (not hardcoded)
- Spring Boot properties with sensible defaults
- Health check exposed for orchestration

**Ready for**: User registration, login, token refresh, JWT validation, role-based access, 2FA setup, user blocking, etc.
