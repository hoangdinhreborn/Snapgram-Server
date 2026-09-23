# Login & Register Service Implementation

## Status

✓ **Created** - Full Login/Register/Refresh/Logout services with:
- Request/Response DTOs
- Input validation
- Exception handling
- JWT token generation & storage
- Role assignment (commented out - needs transaction fix)
- Controllers for all endpoints

⚠️ **Issue** - Hibernate StaleObjectStateException on user registration
- Root cause: Spring Data JPA merge issue with detached entities
- Affects: `userRepository.save()` call after initial insert
- Need to investigate: open-in-view setting, transaction isolation, or entity versioning

## Files Created

### DTOs
- `RegisterRequest.java` - username, email, password, displayName
- `LoginRequest.java` - email, password
- `AuthResponse.java` - accessToken, refreshToken, tokenType, expiresIn, user
- `RefreshTokenRequest.java` - refreshToken

### Exceptions  
- `AuthException.java` - base exception
- `DuplicateUserException.java` - user already exists
- `InvalidCredentialsException.java` - wrong password/token

### Services
- `AuthService.java` - register, login, refreshToken, logout, verifyToken, getUserFromToken
- `RoleService.java` - assignRole, removeRole, hasRole, isAdmin, makeAdmin, removeAdmin

### Controllers
- `AuthController.java` - endpoints for register, login, refresh, logout, verify, getUserInfo

## API Endpoints

### Public Endpoints (No Auth Required)

**Register**
```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "SecurePass123",
  "displayName": "John Doe"
}

Response (201 Created):
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "uuid",
    "username": "john_doe",
    "email": "john@example.com",
    "displayName": "John Doe"
  }
}
```

**Login**
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "john@example.com",
  "password": "SecurePass123"
}

Response (200 OK): Same as register
```

**Refresh Token**
```http
POST /api/auth/refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbG..."
}

Response (200 OK): New access & refresh tokens
```

**Logout**
```http
POST /api/auth/logout
Content-Type: application/json

{
  "refreshToken": "eyJhbG..."
}

Response (204 No Content)
```

### Protected Endpoints (Require Authorization Header)

**Verify Token**
```http
POST /api/auth/verify
Authorization: Bearer <access_token>

Response (200 OK):
true/false
```

**Get User Info**
```http
GET /api/auth/user
Authorization: Bearer <access_token>

Response (200 OK):
{
  "id": "uuid",
  "username": "john_doe",
  "email": "john@example.com",
  "displayName": "John Doe"
}
```

## Service Methods

### AuthService

```java
// Register new user
AuthResponse register(RegisterRequest request)

// Login with email & password
AuthResponse login(LoginRequest request)

// Get new access token using refresh token
AuthResponse refreshToken(RefreshTokenRequest request)

// Logout (revoke refresh token)
void logout(RefreshTokenRequest request)

// Verify if access token is valid
boolean verifyToken(String token)

// Get user info from access token (for other services)
AuthResponse.UserDto getUserFromToken(String token)
```

### RoleService

```java
// Get all roles for user
Collection<String> getUserRoles(UUID userId)

// Check if user has role
boolean hasRole(UUID userId, String role)

// Check if user is admin
boolean isAdmin(UUID userId)

// Assign role to user
void assignRole(UUID userId, String role)

// Remove role from user
void removeRole(UUID userId, String role)

// Promote to admin
void makeAdmin(UUID userId)

// Demote from admin
void removeAdmin(UUID userId)
```

## Implementation Details

### Password Security
- BCrypt hashing with configurable strength
- Minimum 8 characters required
- Never stored in plain text

### JWT Tokens
- **Access Token** (15 minutes TTL)
  - Contains: user ID, username, roles, JTI
  - Used for API authentication
  
- **Refresh Token** (30 days TTL)
  - Stored in database with hash
  - Can be revoked (soft delete with revoked_at)
  - Used to get new access tokens

### Token Storage
- Refresh tokens hashed before storage (SHA-256)
- JTI (JWT ID) indexed for fast lookup
- Expiration timestamp for cleanup
- Revocation timestamp for blacklisting

### Validation
- Username: required, unique, min 3 chars (recommend)
- Email: required, unique, valid format
- Password: required, min 8 chars
- No SQL injection (parameterized queries)
- No XSS (JSON response, never HTML injection)

## Known Issues & Fixes Needed

### 1. Hibernate StaleObjectStateException on Register

**Error**:
```
Row was already updated or deleted by another transaction for entity [AuthUser with id '...']
```

**Possible Causes**:
- `spring.jpa.open-in-view: false` causing lazy loading issues
- Transaction isolation level conflict
- Entity merge after detachment
- Hibernate optimistic locking (@Version field missing)

**Solutions to Try**:
1. Add `@Version` field to AuthUser for optimistic locking
2. Set `spring.jpa.open-in-view: true` (currently set)
3. Use `spring.jpa.hibernate.jdbc.batch_size` for batching
4. Split register into separate transactions
5. Use `entityManager.detach(user)` before generateTokens
6. Use `@Transactional(propagation = REQUIRES_NEW)` for role assignment

**Code to Fix**:
```java
@Entity
@Table(name = "auth_users")
public class AuthUser {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    // Add this for optimistic locking
    @Version
    private Long version;
    
    // ... rest of fields
}
```

### 2. Role Assignment Not Working

Currently commented out in register method - need to fix transaction issues first.

### 3. Token Hashing

Currently using `Utf8.encode(token).toString()` which is not secure. Should use:
```java
private String hashToken(String token) {
    return DigestUtils.sha256Hex(token);
}
```

## Testing Checklist

- [ ] Register with valid data → returns tokens
- [ ] Register with duplicate username → 409 Conflict
- [ ] Register with duplicate email → 409 Conflict
- [ ] Register with weak password → 400 Bad Request
- [ ] Login with correct credentials → returns tokens
- [ ] Login with wrong password → 401 Unauthorized
- [ ] Login with non-existent user → 401 Unauthorized
- [ ] Refresh with valid refresh token → new tokens
- [ ] Refresh with expired token → 401 Unauthorized
- [ ] Refresh with revoked token → 401 Unauthorized
- [ ] Logout revokes token → subsequent refresh fails
- [ ] Verify valid token → true
- [ ] Verify invalid token → false
- [ ] Get user info with valid token → user data
- [ ] Get user info with invalid token → 401 Unauthorized

## Next Steps

1. **Fix Hibernate Issue** (CRITICAL)
   - Add @Version field to AuthUser
   - Test register endpoint

2. **Enable Role Assignment**
   - Uncomment roleService.assignRole() in register
   - Test that USER role is assigned

3. **Add Email Verification** (Future)
   - Send verification email on register
   - Verify email before allowing login
   - Resend verification link

4. **Add 2FA Support** (Future)
   - Setup TOTP
   - Verify TOTP code at login

5. **Add Audit Logging**
   - Log all register/login/logout events
   - Log failed login attempts
   - Track role changes

6. **API Documentation**
   - Generate OpenAPI/Swagger docs
   - Add endpoint descriptions
   - Document error codes

## Security Considerations

✓ Passwords hashed with BCrypt
✓ Tokens signed with HMAC-SHA256
✓ Refresh tokens revocable
✓ JWT expiration enforced
✓ Non-root user in container

⚠️ TODO:
- Rate limiting on register/login
- CSRF protection
- CORS configuration
- API key for inter-service calls
- Request/response logging (without sensitive data)
