# Snapgram Inter-Service Communication & API Gateway Guide

## Overview

Snapgram microservices giao tiếp với nhau qua **HTTP/REST** bên trong Docker network. API Gateway (port 8080) là entry point duy nhất để external clients gọi các services.

## Architecture

```
Client (External)
    ↓
API Gateway (8080)
    ├→ Auth Service (8081)
    ├→ Chat Service (8082)
    ├→ Content Service (8084)
    ├→ Media Service (8085)
    └→ Other Services...

Internal Communication (Docker Network):
Service A ←→ Service B (direct HTTP calls)
```

## Docker Network Communication

Khi services chạy trong Docker Compose, chúng ở trên cùng một **bridge network** mặc định. Mỗi service có thể gọi service khác thông qua **service name** (không cần IP):

### Service Names (Hostnames)

```
- auth-service
- chat-service
- chatbot-service
- content-service
- media-service
- notification-service
- recommender-service
- api-gateway
```

### Example: Service A Gọi Service B

**Service A (Chat Service) muốn gọi Auth Service**:

```java
// URL format trong container
String authServiceUrl = "http://auth-service:8081/api/auth/verify";

// Dùng RestTemplate hoặc WebClient
RestTemplate restTemplate = new RestTemplate();
ResponseEntity<AuthResponse> response = restTemplate.getForEntity(authServiceUrl, AuthResponse.class);
```

**Key point**: Dùng **service name** + **internal port** (không phải localhost:8081 từ ngoài)

## Inter-Service Communication Patterns

### 1. Synchronous (HTTP/REST)

**Chat Service → Auth Service (verify JWT)**

```java
@Service
public class AuthClient {
    
    private final RestTemplate restTemplate;
    
    public AuthClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    public boolean verifyToken(String token) {
        try {
            String url = "http://auth-service:8081/api/auth/verify";
            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(token),
                AuthResponse.class
            );
            return response.getBody().isValid();
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2. Asynchronous (Kafka)

**Service A publishes event → Kafka → Service B consumes**

```java
// Producer (Content Service)
@Service
public class ContentEventPublisher {
    
    private final KafkaTemplate<String, ContentEvent> kafkaTemplate;
    
    public void publishContentCreated(Content content) {
        ContentEvent event = new ContentEvent(
            content.getId(),
            content.getAuthorId(),
            "CONTENT_CREATED"
        );
        kafkaTemplate.send("content-events", event);
    }
}

// Consumer (Notification Service)
@Service
public class ContentEventListener {
    
    @KafkaListener(topics = "content-events", groupId = "notification-service")
    public void handleContentCreated(ContentEvent event) {
        // Gửi notification khi content mới được tạo
        notificationService.sendNotification(
            event.getAuthorId(),
            "Your content was published!"
        );
    }
}
```

### 3. Shared Cache (Redis)

Services chia sẻ data qua Redis:

```java
// Service A: Set cache
@Service
public class UserCacheService {
    
    private final RedisTemplate<String, User> redisTemplate;
    
    public void cacheUser(User user) {
        redisTemplate.opsForValue().set(
            "user:" + user.getId(),
            user,
            Duration.ofHours(1)
        );
    }
}

// Service B: Get cache
@Service
public class UserService {
    
    private final RedisTemplate<String, User> redisTemplate;
    
    public User getUser(Long userId) {
        User cached = (User) redisTemplate.opsForValue().get("user:" + userId);
        if (cached != null) {
            return cached;
        }
        // Fall back to database
        return userRepository.findById(userId).orElse(null);
    }
}
```

## API Gateway Configuration

API Gateway (Spring Cloud Gateway) routes external requests đến các services:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
        
        - id: chat-service
          uri: http://chat-service:8082
          predicates:
            - Path=/api/chat/**
          filters:
            - StripPrefix=1
        
        - id: content-service
          uri: http://content-service:8084
          predicates:
            - Path=/api/content/**
          filters:
            - StripPrefix=1
```

### How It Works

```
External Client:
  GET /api/auth/login

API Gateway:
  ✓ Match route: /api/auth/** → auth-service
  ✓ Strip prefix: /api/auth → /
  ✓ Forward to: http://auth-service:8081/login
  ✓ Return response to client
```

**Benefits**:
- Single entry point (8080)
- Load balancing
- Request filtering/modification
- Rate limiting
- Authentication/Authorization

## Service Discovery (Optional)

Hiện tại dùng **static URLs** với service names. Để scale hoặc dynamic discovery:

### Consul/Eureka (Service Registry)

```java
@SpringBootApplication
@EnableDiscoveryClient
public class AuthServiceApplication {
    // Service auto-registers vào Consul/Eureka
}

// Client uses service name (not URL)
@LoadBalanced
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// Usage
restTemplate.getForObject("http://auth-service/api/...", Response.class);
```

## Communication Best Practices

### 1. **Timeouts & Retries**

```java
@Configuration
public class HttpClientConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);      // 5s connect
        factory.setReadTimeout(10000);        // 10s read
        
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}
```

### 2. **Circuit Breaker (Resilience4j)**

```java
@Service
public class ResilientAuthClient {
    
    private final RestTemplate restTemplate;
    private final CircuitBreaker circuitBreaker;
    
    public ResilientAuthClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
        this.circuitBreaker = CircuitBreaker.of("auth-service", config);
    }
    
    public AuthResponse verify(String token) {
        return circuitBreaker.executeSupplier(() -> 
            restTemplate.postForObject(
                "http://auth-service:8081/api/auth/verify",
                token,
                AuthResponse.class
            )
        );
    }
}
```

### 3. **Distributed Tracing (Optional)**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

Logs từ tất cả services có `traceId` để track request flow.

### 4. **Authentication Between Services**

**Option 1: Propagate JWT**
```java
// Service A calls Service B, forward JWT token
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + token);
restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Response.class);
```

**Option 2: Service-to-Service Token (OAuth2)**
```java
// Service A requests own token từ Auth Service
String serviceToken = authClient.getServiceToken("chat-service");
// Use token cho internal calls
```

### 5. **Error Handling**

```java
@Service
public class SafeServiceClient {
    
    public UserResponse getUser(Long userId) {
        try {
            return restTemplate.getForObject(
                "http://user-service:8081/api/users/" + userId,
                UserResponse.class
            );
        } catch (HttpClientErrorException.NotFound e) {
            return null; // User not found
        } catch (RestClientException e) {
            logger.error("Service unavailable", e);
            return new UserResponse(); // Fallback
        }
    }
}
```

## Docker Network Details

### View Network

```bash
docker network ls
docker network inspect snapgram_infra_default
```

### DNS Resolution in Containers

```bash
# Inside container, service name resolves to internal IP
docker compose exec auth-service nslookup chat-service
```

Output:
```
Name:      chat-service
Address:   172.20.0.5
```

### Port Accessibility

- **From outside Docker**: `localhost:8082` (mapped port)
- **From inside Docker**: `chat-service:8082` (service name + internal port)

## Example Flow: User Registration

```
1. Client POST /api/auth/register
   ↓
2. API Gateway routes to Auth Service (8081)
   ↓
3. Auth Service:
   - Validate user data
   - Check if user exists (internal Redis call)
   - Store user in PostgreSQL
   - Publish USER_CREATED event to Kafka
   ↓
4. Notification Service (listening on Kafka):
   - Consume USER_CREATED event
   - Send welcome email via Mailpit
   ↓
5. Recommender Service (listening on Kafka):
   - Consume USER_CREATED event
   - Initialize user recommendations
   ↓
6. Return success response to Client
```

## Debugging Inter-Service Calls

### 1. Check Service is Running

```bash
docker compose ps
```

### 2. Test Connection from Another Service

```bash
docker compose exec chat-service curl http://auth-service:8081/actuator/health
```

### 3. View Logs

```bash
docker compose logs -f auth-service chat-service
```

### 4. Inspect Network

```bash
docker compose exec auth-service nslookup chat-service
```

## Summary

| Pattern | Use Case | Tools |
|---------|----------|-------|
| **REST/HTTP** | Real-time requests, small payloads | RestTemplate, WebClient |
| **Kafka** | Async events, decoupling, scalability | Spring Kafka, Kafka topics |
| **Redis** | Shared cache, sessions | RedisTemplate, Spring Data Redis |
| **gRPC** | High-performance, low latency (future) | gRPC, Protocol Buffers |

**Recommendation**: Start with REST + Kafka. Add circuit breaker & retries for resilience.
