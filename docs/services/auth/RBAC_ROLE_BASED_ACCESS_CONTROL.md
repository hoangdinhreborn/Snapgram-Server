# Role-Based Access Control (RBAC) in Auth Service

## Overview

Auth Service implement **Role-Based Access Control (RBAC)** to separate admin and regular users.

## Roles

### Available Roles

| Role | Description | Permissions |
|------|-------------|-------------|
| **USER** | Default role for all users | Standard user operations |
| **ADMIN** | Administrator role | Admin endpoints, user management |
| **MODERATOR** | Moderator (future) | Content moderation |

## Database Schema

### `auth_user_roles` Table

```sql
CREATE TABLE auth_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_user_roles_user_role UNIQUE (user_id, role)
);
```

**Key Points**:
- `UNIQUE (user_id, role)` - Each user can have each role only once
- `ON DELETE CASCADE` - Roles deleted when user deleted
- Example: User can have both `ADMIN` and `USER` roles simultaneously

## Code Structure

### 1. RoleService

Manage user roles programmatically:

```java
@Service
public class RoleService {
    
    // Get all roles for user
    public Collection<String> getUserRoles(UUID userId)
    
    // Check if user has role
    public boolean hasRole(UUID userId, String role)
    
    // Check if user is admin
    public boolean isAdmin(UUID userId)
    
    // Assign role to user
    @Transactional
    public void assignRole(UUID userId, String role)
    
    // Remove role from user
    @Transactional
    public void removeRole(UUID userId, String role)
    
    // Promote to admin
    @Transactional
    public void makeAdmin(UUID userId)
    
    // Demote from admin
    @Transactional
    public void removeAdmin(UUID userId)
}
```

### 2. SecurityConfig

Configure Spring Security with role-based authorization:

```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // Public endpoints
            .requestMatchers(
                "/api/auth/register",
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout",
                "/actuator/health"
            ).permitAll()
            // Admin-only endpoints
            .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
            // All other endpoints require authentication
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

### 3. JwtAuthenticationFilter

Extract roles from JWT token and set SecurityContext:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) {
        // 1. Extract token from Authorization header
        String token = authorization.substring(7); // "Bearer xxx"
        
        // 2. Validate token
        if (!jwtService.isAccessToken(token)) {
            // Not an access token, skip
        }
        
        // 3. Extract roles from JWT claims
        List<SimpleGrantedAuthority> authorities = jwtService.getRoles(token)
            .stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList();
        
        // 4. Set SecurityContext
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                userId,
                null,
                authorities
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
```

## Usage Examples

### 1. Using @PreAuthorize Annotation

```java
@Service
public class AdminService {
    
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(UUID userId) {
        // Only ADMIN can call this
    }
    
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    public void suspendUser(UUID userId) {
        // ADMIN or MODERATOR can call this
    }
    
    @PreAuthorize("authenticated")
    public void updateProfile(UUID userId, Profile profile) {
        // Any authenticated user can call this
    }
}
```

### 2. Programmatic Check

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final RoleService roleService;
    private final SecurityContext securityContext;
    
    public void promoteUser(UUID userId) {
        // Get current user
        UUID currentUserId = (UUID) SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getPrincipal();
        
        // Check if current user is admin
        if (!roleService.isAdmin(currentUserId)) {
            throw new AccessDeniedException("Only admins can promote users");
        }
        
        // Promote user
        roleService.makeAdmin(userId);
    }
}
```

### 3. In Controller

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final RoleService roleService;
    
    // Public endpoint
    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        // No auth required
    }
    
    // Admin-only endpoint
    @PostMapping("/admin/users/{userId}/promote")
    @PreAuthorize("hasRole('ADMIN')")
    public void promoteUser(@PathVariable UUID userId) {
        roleService.makeAdmin(userId);
    }
    
    // Authenticated endpoint
    @GetMapping("/profile")
    @PreAuthorize("authenticated")
    public UserProfile getProfile(@AuthenticationPrincipal UUID userId) {
        // userId is current user
    }
}
```

## JWT Token Structure

Access token includes roles claim:

```json
{
  "jti": "550e8400-e29b-41d4-a716-446655440000",
  "sub": "user-uuid",
  "iss": "snapgram-auth",
  "iat": 1234567890,
  "exp": 1234568890,
  "username": "john_doe",
  "token_type": "access",
  "roles": ["USER", "ADMIN"]
}
```

Spring Security automatically converts `roles` claim to `ROLE_` prefixed authorities:
- JWT: `"roles": ["ADMIN"]`
- Spring: `ROLE_ADMIN` authority

## API Endpoints for Role Management

### Get User Roles (Admin-only)
```http
GET /api/auth/admin/users/{userId}/roles
Authorization: Bearer <admin-token>

Response:
{
  "userId": "user-uuid",
  "roles": ["USER", "ADMIN"]
}
```

### Assign Role (Admin-only)
```http
POST /api/auth/admin/users/{userId}/roles
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "role": "ADMIN"
}
```

### Remove Role (Admin-only)
```http
DELETE /api/auth/admin/users/{userId}/roles/{role}
Authorization: Bearer <admin-token>
```

### Promote User (Admin-only)
```http
POST /api/auth/admin/users/{userId}/promote
Authorization: Bearer <admin-token>
```

### Demote User (Admin-only)
```http
POST /api/auth/admin/users/{userId}/demote
Authorization: Bearer <admin-token>
```

## Role Assignment Strategy

### Registration Flow
1. User registers via `/api/auth/register`
2. System automatically assigns `USER` role
3. Only ADMIN can assign `ADMIN` role later

```java
@PostMapping("/register")
public AuthResponse register(@RequestBody RegisterRequest request) {
    // Create user
    AuthUser user = createUser(request);
    
    // Assign USER role
    roleService.assignRole(user.getId(), "USER");
    
    // Return JWT with USER role
    return generateTokens(user);
}
```

### Admin Creation
Option 1: Bootstrap initial admin in database
```sql
INSERT INTO auth_users (id, username, email, password_hash, created_at, updated_at)
VALUES ('admin-uuid', 'admin', 'admin@snapgram.com', '$2a$10$...', NOW(), NOW());

INSERT INTO auth_user_roles (id, user_id, role, created_at)
VALUES ('role-uuid', 'admin-uuid', 'ADMIN', NOW());
```

Option 2: Service endpoint (only for initial setup)
```bash
curl -X POST http://localhost:8081/api/auth/bootstrap \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "secure-password"}'
```

## Security Best Practices

### 1. Always Check Roles at Boundary

```java
// ❌ Bad: Check only in service
public void deleteUser(UUID userId) {
    userRepository.deleteById(userId);
}

// ✓ Good: Check at controller with @PreAuthorize
@DeleteMapping("/users/{userId}")
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(@PathVariable UUID userId) {
    userRepository.deleteById(userId);
}
```

### 2. Don't Trust User Input for Roles

```java
// ❌ Bad: User can assign own role
@PostMapping("/users/{userId}/roles")
public void assignRole(@PathVariable UUID userId, @RequestBody RoleRequest req) {
    roleService.assignRole(userId, req.getRole());
}

// ✓ Good: Only admin can assign
@PostMapping("/admin/users/{userId}/roles")
@PreAuthorize("hasRole('ADMIN')")
public void assignRole(@PathVariable UUID userId, @RequestBody RoleRequest req) {
    roleService.assignRole(userId, req.getRole());
}
```

### 3. Log Role Changes

```java
@Transactional
public void assignRole(UUID userId, String role) {
    if (!roleRepository.existsByUserIdAndRole(userId, role)) {
        AuthUserRole userRole = new AuthUserRole();
        userRole.setId(UUID.randomUUID());
        userRole.setUserId(userId);
        userRole.setRole(role);
        userRole.setCreatedAt(Instant.now());
        roleRepository.save(userRole);
        
        // Log for audit trail
        auditLog.log(userId, "ROLE_ASSIGNED", role);
    }
}
```

### 4. Revoke Tokens on Role Change

```java
@Transactional
public void removeAdmin(UUID userId) {
    removeRole(userId, "ADMIN");
    
    // Revoke all refresh tokens to force re-login
    refreshTokenService.revokeUserTokens(userId);
}
```

## Testing Roles

### Unit Test

```java
@Test
public void testAdminAccess() {
    // Create admin user with ADMIN role
    AuthUser admin = createUser("admin");
    roleService.assignRole(admin.getId(), "ADMIN");
    
    // Verify role
    assertTrue(roleService.isAdmin(admin.getId()));
}

@Test
public void testNonAdminDenied() {
    // Create user without ADMIN role
    AuthUser user = createUser("user");
    roleService.assignRole(user.getId(), "USER");
    
    // Verify role
    assertFalse(roleService.isAdmin(user.getId()));
}
```

### Integration Test

```java
@SpringBootTest
public class RbacIntegrationTest {
    
    @Test
    public void testAdminEndpoint() {
        String adminToken = generateToken("admin", List.of("ADMIN"));
        
        // Should succeed
        mockMvc.perform(
            post("/api/auth/admin/users/123/promote")
                .header("Authorization", "Bearer " + adminToken)
        ).andExpect(status().isOk());
    }
    
    @Test
    public void testUserDenied() {
        String userToken = generateToken("user", List.of("USER"));
        
        // Should be denied
        mockMvc.perform(
            post("/api/auth/admin/users/123/promote")
                .header("Authorization", "Bearer " + userToken)
        ).andExpect(status().isForbidden());
    }
}
```

## Summary

✓ **RBAC Implemented**:
- `USER` role assigned on registration
- `ADMIN` role assigned by admins only
- Spring Security enforces authorization

✓ **Multiple Authorization Methods**:
- `@PreAuthorize` annotation (declarative)
- `RoleService` programmatic check
- `SecurityContextHolder` for current user

✓ **Secure Design**:
- Roles stored in database (scalable)
- Roles included in JWT (efficient, cached)
- Role changes require token revocation

**Next Steps**: Implement admin endpoints, role management API, audit logging for role changes.
