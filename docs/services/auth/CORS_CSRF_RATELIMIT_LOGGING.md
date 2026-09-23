# CORS, CSRF, Rate Limiting & Logging Configuration

## Overview

Auth Service is configured with enterprise-grade security and observability:
- **CORS**: Allow cross-origin requests from whitelisted domains
- **CSRF**: Token-based CSRF protection for state-changing operations
- **Rate Limiting**: Bucket4j-based request throttling (100 req/min per IP)
- **Logging**: Comprehensive request/response logging with correlation IDs

## CORS (Cross-Origin Resource Sharing)

### Configuration

File: `CorsConfig.java`

```yaml
Allowed Origins:
  - http://localhost:3000 (Frontend dev)
  - http://localhost:5173 (Vite)
  - https://snapgram.local (Production)

Allowed Methods:
  - GET, POST, PUT, DELETE, PATCH, OPTIONS

Allowed Headers:
  - Content-Type
  - Authorization
  - X-Requested-With
  - Accept
  - Origin
  - X-CSRF-Token
  - And others...

Exposed Headers:
  - Authorization
  - X-CSRF-Token
  - Content-Type

Credentials: ✓ Allowed (cookies, auth headers)
Preflight Cache: 1 hour
```

### Frontend Usage

```javascript
// Fetch with CORS
fetch('http://localhost:8081/api/auth/login', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'Authorization': 'Bearer <token>'
  },
  credentials: 'include', // Send cookies
  body: JSON.stringify({
    email: 'user@example.com',
    password: 'password'
  })
})
```

### Adding New Origins

Edit `CorsConfig.java`:

```java
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "https://new-domain.com"
));
```

Redeploy service.

## CSRF (Cross-Site Request Forgery) Protection

### Configuration

File: `SecurityConfig.java`

**CSRF is DISABLED for stateless API endpoints**:
- `/api/auth/login`
- `/api/auth/register`
- `/api/auth/refresh`
- `/api/auth/verify`
- `/actuator/**`

**Reason**: JWT-based authentication is inherently CSRF-safe (no cookies for session state)

### How It Works

For protected endpoints that use sessions (if added later):
1. Server generates CSRF token
2. Client includes token in `X-CSRF-Token` header
3. Server validates token matches session

```javascript
// Get CSRF token from response header
const csrfToken = response.headers.get('X-CSRF-Token');

// Use in subsequent requests
fetch('/api/auth/logout', {
  method: 'POST',
  headers: {
    'X-CSRF-Token': csrfToken
  }
})
```

### Stateless (JWT) vs Stateful (Session)

**Current Setup** (Stateless JWT):
- ✓ No session cookies
- ✓ No CSRF vulnerability
- ✓ Scalable across multiple servers

**If Using Sessions** (Stateful):
- CSRF tokens required
- Configured but disabled currently

## Rate Limiting

### Configuration

File: `RateLimitInterceptor.java`

```
Limit: 100 requests per minute
Key: Client IP + Endpoint
Bucket4j: Token bucket algorithm
```

### How It Works

1. Each unique IP + endpoint combination gets a bucket
2. Bucket has 100 tokens, refills every 60 seconds
3. Each request consumes 1 token
4. When empty → **429 Too Many Requests**

### Response Headers

```http
HTTP/1.1 429 Too Many Requests
X-Rate-Limit-Limit: 100
X-Rate-Limit-Remaining: 42
X-Rate-Limit-Reset: 1695465180

{"error": "Rate limit exceeded. Please try again later."}
```

### Endpoints Excluded from Rate Limit

- `/api/auth/verify` (inter-service verification)
- Reason: Services need reliable calls without rate limits

### Configuring Limits

Edit `RateLimitInterceptor.java`:

```java
private Bucket createNewBucket() {
    // Change these values
    Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
    return Bucket4j.builder()
            .addLimit(limit)
            .build();
}
```

Examples:
```java
// 1000 requests per hour
Refill.intervally(1000, Duration.ofHours(1))

// 10 requests per second
Refill.intervally(10, Duration.ofSeconds(1))

// 50 requests per 5 minutes
Refill.intervally(50, Duration.ofMinutes(5))
```

### Per-User Rate Limiting (Future)

Currently rate limiting is per IP. To limit per authenticated user:

```java
private String getClientKey(HttpServletRequest request) {
    // Extract user ID from JWT token
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
        String token = authHeader.substring(7);
        String userId = jwtService.getUserId(token).toString();
        return "user:" + userId;
    }
    
    // Fallback to IP for unauthenticated requests
    return getClientIp(request) + ":" + request.getRequestURI();
}
```

## Logging

### Configuration

File: `application.yml`

```yaml
logging:
  level:
    root: INFO
    com.example.auth: DEBUG
    org.springframework.security: DEBUG
    org.springframework.web: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/auth-service.log
  logback:
    rollingpolicy:
      max-file-size: 10MB
      max-history: 30
```

### Log Levels

| Level | Use Case | Example |
|-------|----------|---------|
| **ERROR** | Failures, exceptions | Authentication failed, DB error |
| **WARN** | Suspicious activity | Invalid password, rate limit, CSRF token missing |
| **INFO** | Business events | User registered, token refreshed |
| **DEBUG** | Development troubleshooting | SQL queries, auth header content |
| **TRACE** | Parameter binding | SQL parameter values |

### Request/Response Logging

File: `LoggingInterceptor.java`

Every HTTP request logs:

```log
[uuid-request-id] POST /api/auth/login from 192.168.1.1 | Query: N/A
[uuid-request-id] Content-Type: application/json
[uuid-request-id] Authorization: Bearer eyJhbGc...
[uuid-request-id] POST /api/auth/login completed in 245ms | Status: 200
```

### Correlation IDs

Each request gets a unique ID in header:

```http
Response Headers:
X-Request-Id: 550e8400-e29b-41d4-a716-446655440000
```

Use this ID to track request across logs:

```bash
# Find all logs for a request
grep "550e8400-e29b-41d4-a716-446655440000" logs/auth-service.log
```

### Log Files

```
logs/
├── auth-service.log              # Current log
├── auth-service.log.2024-09-22  # Rolled over daily
├── auth-service.log.2024-09-21
└── ...                           # Max 30 days of history
```

**Size**: Max 10MB per file, rolls over to new file

### Sensitive Data Masking

Logs automatically mask sensitive info:

```java
// Authorization header masked
Authorization: Bearer eyJhbGc... (first 20 chars only)

// Passwords NEVER logged
// JWT tokens partially shown

// Request/response body logged only in DEBUG mode
```

### Log Patterns

#### Request Pattern
```log
[REQUEST-ID] METHOD URI from IP | Query: QUERY_STRING
```

#### Response Pattern
```log
[REQUEST-ID] METHOD URI completed in DURATIONms | Status: CODE
```

#### Error Pattern
```log
[REQUEST-ID] METHOD URI completed with error in DURATIONms | Status: CODE (REASON) | Error: MESSAGE
```

### Querying Logs

```bash
# All auth service logs
tail -f logs/auth-service.log

# Failed login attempts
grep "Login failed" logs/auth-service.log

# Rate limit hits
grep "Rate limit exceeded" logs/auth-service.log

# Slow requests (>1 second)
grep -E "completed in [1-9][0-9]{3}ms" logs/auth-service.log

# Specific user/email
grep "john@example.com" logs/auth-service.log

# Status 500 errors
grep "Status: 500" logs/auth-service.log

# Within time range
grep "2024-09-23 14:" logs/auth-service.log
```

## Testing All Features

### 1. CORS Test

```bash
# Preflight request (OPTIONS)
curl -X OPTIONS http://localhost:8081/api/auth/login \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -v

# Should return 200 with CORS headers
```

### 2. CSRF Test

```bash
# Regular POST (CSRF disabled for /api/auth/*)
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"pass"}' \
  -v
```

### 3. Rate Limiting Test

```bash
# Hit endpoint 101 times quickly
for i in {1..101}; do
  curl http://localhost:8081/api/auth/verify \
    -H "Authorization: Bearer token" &
done

# Request 101 should get 429
```

### 4. Logging Test

```bash
# Make a request
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid"

# Check logs
tail -50 logs/auth-service.log

# Should see: request ID, method, IP, response status, duration
```

## Production Checklist

- [ ] Update `CorsConfig.java` with production domains only
- [ ] Increase rate limits if needed
- [ ] Set logging level to INFO (reduce DEBUG/TRACE)
- [ ] Configure log rotation policy (30 days max)
- [ ] Set up log aggregation (ELK, Splunk, etc.)
- [ ] Monitor 429 responses (suspicious activity)
- [ ] Monitor 401/403 responses (auth failures)
- [ ] Alert on rate limit spikes
- [ ] Setup HTTPS/TLS (CORS requires https://)
- [ ] Verify CSRF token handling if using sessions

## Troubleshooting

### CORS Errors

```
Access to XMLHttpRequest blocked by CORS policy
```

**Solution**:
1. Verify origin in `CorsConfig.java`
2. Check request headers match allowed headers
3. Check preflight OPTIONS request succeeds

### Rate Limit Blocking Legitimate Traffic

```
429 Too Many Requests
```

**Solution**:
1. Increase limit: `Bandwidth.classic(500, ...)`
2. Exclude endpoint if it's service-to-service
3. Check if IP is being shared by multiple clients (NAT/proxy)

### Missing Logs

```
Logs not appearing in logs/auth-service.log
```

**Solution**:
1. Check log level: `logging.level.com.example.auth: DEBUG`
2. Verify file path: `logs/auth-service.log` (relative to container)
3. Check container has write permissions to log directory
4. View Docker logs: `docker logs snapgram-auth-service`
