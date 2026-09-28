# Snapgram Server

Dự án **Snapgram** là một hệ thống mạng xã hội được xây dựng theo kiến trúc **microservices** sử dụng **Java / Spring Boot**. Tài liệu này dành cho **tất cả thành viên trong team**, cung cấp thông tin cần thiết để bắt đầu làm việc nhanh chóng.

---

## Mục lục

1. [Danh sách service & port](#1-danh-sách-service--port)
2. [Kiến trúc xác thực & phân quyền Admin — cách dùng UserContext](#2-kiến-trúc-xác-thực--phân-quyền-admin--cách-dùng-usercontext)
3. [Giao tiếp giữa các service](#3-giao-tiếp-giữa-các-service)
4. [Hướng dẫn chạy project](#4-hướng-dẫn-chạy-project)
5. [Quy trình làm việc](#5-quy-trình-làm-việc)
6. [Tài liệu tham khảo thêm](#6-tài-liệu-tham-khảo-thêm)

---

## 1. Danh sách service & port

| Service               | Mô tả                                  | Port ngoài | Host trong Docker           |
|-----------------------|----------------------------------------|------------|-----------------------------|
| `api-gateway`         | Cổng duy nhất tiếp nhận request ngoài | `8080`     | `api-gateway:8080`          |
| `auth-service`        | Đăng ký, đăng nhập, quản lý token     | `8081`     | `auth-service:8081`         |
| `chat-service`        | Nhắn tin real-time                     | `8082`     | `chat-service:8082`         |
| `content-service`     | Bài viết, bình luận, like             | `8084`     | `content-service:8084`      |
| `media-service`       | Upload & quản lý ảnh/video             | `8085`     | `media-service:8085`        |
| `notification-service`| Gửi thông báo (email, push)            | `8086`     | `notification-service:8086` |
| `chatbot-service`     | Tích hợp AI chatbot                    | `8087`     | `chatbot-service:8087`      |
| `recommender-service` | Gợi ý nội dung / người dùng           | `8088`     | `recommender-service:8088`  |

---

## 2. Kiến trúc xác thực & phân quyền Admin — cách dùng UserContext

> ⚡ **Quy tắc quan trọng nhất của project:**
> **API Gateway là nơi DUY NHẤT xác minh JWT.** Sau khi xác minh thành công, Gateway trích xuất thông tin người dùng từ payload và đính kèm vào các headers:
> - `X-User-Id`: UUID của user
> - `X-Username`: Username của user
> - `X-User-Roles`: Danh sách role phân tách bằng dấu phẩy (vd: `USER,ADMIN`)
> Các service downstream **KHÔNG cần** tự verify JWT hay gọi lại `auth-service`, chỉ cần đọc thông tin thông qua class tiện ích `UserContext`.

### 2.1 Luồng xác thực & phân quyền

```
┌─────────────────────────────────────────────────────────────────┐
│  CLIENT                                                         │
│  Request: POST /api/admin/reports/123/resolve                   │
│  Header:  Authorization: Bearer <jwt_token>                     │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  API GATEWAY  (port 8080)                                       │
│                                                                 │
│  1. Nhận request từ client                                      │
│  2. Gọi auth-service /api/auth/verify (check signature, hạn,    │
│     blacklist token)                                            │
│  3. Nếu hợp lệ → trích xuất: sub (userId), username, roles      │
│  4. Đính kèm downstream headers:                                │
│       X-User-Id: <userId>                                       │
│       X-Username: <username>                                    │
│       X-User-Roles: USER,ADMIN                                  │
│  5. Forward request đến service đích                            │
│  6. Nếu không hợp lệ → trả về 401 Unauthorized ngay            │
└────────────────────────────┬────────────────────────────────────┘
                             │  forward (kèm các headers, không còn JWT)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│  DOWNSTREAM SERVICE  (vd: content-service:8084)                 │
│                                                                 │
│  ✅ UserContext.getCurrentUserId() → nhận UUID                   │
│  ✅ UserContext.isAdmin()          → kiểm tra quyền Admin       │
│  ✅ UserContext.requireAdmin()     → chặn (403) nếu không phải  │
│  ❌ KHÔNG cần: verify JWT, @RequestHeader, gọi auth-service     │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Pattern chuẩn — dùng `UserContext`

Mỗi service tạo một class `UserContext` trong package `util`. Đây là cách **toàn bộ team phải làm thống nhất**:

**`util/UserContext.java`**

```java
package com.example.<service>.util;

import com.example.<service>.exception.AccessDeniedException;
import com.example.<service>.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID    = "X-User-Id";
    public static final String HEADER_USERNAME   = "X-Username";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private UserContext() {}

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("No active HTTP request context");
        }
        return attributes.getRequest();
    }

    /**
     * Lấy UUID của người dùng hiện tại từ header X-User-Id.
     * Ném UnauthorizedException (401) nếu thiếu hoặc sai format.
     */
    public static UUID getCurrentUserId() {
        String userIdStr = getRequest().getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid X-User-Id format: must be a valid UUID");
            }
        }
        throw new UnauthorizedException("Missing authentication: X-User-Id header is required");
    }

    /**
     * Lấy Username từ header X-Username.
     */
    public static String getUsername() {
        String username = getRequest().getHeader(HEADER_USERNAME);
        return username != null ? username.trim() : "";
    }

    /**
     * Lấy danh sách Roles từ header X-User-Roles (ví dụ: ["USER", "ADMIN"]).
     */
    public static List<String> getUserRoles() {
        String rolesStr = getRequest().getHeader(HEADER_USER_ROLES);
        if (rolesStr == null || rolesStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Kiểm tra user hiện tại có sở hữu role cụ thể hay không.
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) return false;
        String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;
        return getUserRoles().stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .anyMatch(r -> r.equalsIgnoreCase(normalized));
    }

    /**
     * Kiểm tra nhanh user hiện tại có role ADMIN hay không.
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Bảo vệ endpoint Admin: nếu không phải ADMIN, ném AccessDeniedException (HTTP 403 Forbidden).
     */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied: ADMIN role required");
        }
    }
}
```

### 2.3 Cách dùng trong Controller

**Dùng cho API người dùng thông thường:**
```java
@PostMapping
public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
    UUID currentUserId = UserContext.getCurrentUserId(); // ← lấy userId ở đây
    return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(currentUserId, request));
}
```

**Dùng cho API Admin / Quản trị viên:**
```java
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ResponseEntity<Page<ReportResponse>> getPendingReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Chỉ cần gọi 1 dòng: nếu không phải ADMIN, tự throw AccessDeniedException (403)
        UserContext.requireAdmin();

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return ResponseEntity.ok(reportService.getPendingReports(pageable));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveReport(@PathVariable UUID id) {
        UserContext.requireAdmin();
        UUID adminId = UserContext.getCurrentUserId();
        reportService.resolveReport(id, adminId);
        return ResponseEntity.noContent().build();
    }
}
```

> 💡 `UserContext` hoạt động nhờ `RequestContextHolder` của Spring — lấy được request hiện tại từ bất kỳ đâu trong thread xử lý mà không cần truyền tham số qua các tầng.

### 2.4 Quy chuẩn phân chia API Admin theo từng Service

| Service | Đường dẫn API Admin | Nghiệp vụ xử lý |
|---|---|---|
| `auth-service` | `/api/admin/users/**` | Khóa/mở khóa tài khoản (ban/unban), gán role Admin/Mod, quản lý user |
| `content-service` | `/api/admin/reports/**`, `/api/admin/content/**` | Xem danh sách report, duyệt/bác bỏ report, xóa/ẩn bài viết vi phạm |
| `media-service` | `/api/admin/media/**` | Xóa media vi phạm bản quyền/chính sách, thống kê dung lượng lưu trữ |

### 2.5 Lưu ý quan trọng

- ✅ Đặt `UserContext.java` trong package `util` của từng service.
- ✅ `getCurrentUserId()` trả về `UUID` — không phải `String`.
- ✅ Dùng `UserContext.requireAdmin()` ở đầu method API admin để chặn quyền sớm và trả về HTTP `403 Forbidden`.
- ❌ **Không** dùng `@RequestHeader("X-User-Id")` trong method signature — dùng `UserContext` thay thế.
- ❌ **Không** expose port nội bộ (8081, 8082...) ra ngoài máy chủ — các headers `X-User-*` chỉ tin cậy khi đến qua Gateway.
- ✅ Khi viết unit test, mock `RequestContextHolder` hoặc dùng `MockMvc` với headers:

```java
mockMvc.perform(get("/api/admin/reports")
        .header("X-User-Id", UUID.randomUUID().toString())
        .header("X-User-Roles", "ADMIN,USER"))
        .andExpect(status().isOk());
```

---

## 3. Giao tiếp giữa các service

### 3.1 Internal HTTP (đồng bộ)

Các service gọi nhau qua hostname Docker (không dùng `localhost`):

```java
// Content Service gọi Media Service
String url = "http://media-service:8085/api/media/" + mediaId;
ResponseEntity<MediaResponse> response = restTemplate.getForEntity(url, MediaResponse.class);
```

> **Luôn dùng service name** (vd: `media-service`) thay vì IP hoặc `localhost`.

### 3.2 Kafka (bất đồng bộ)

Dùng cho các sự kiện không yêu cầu phản hồi ngay:

| Topic               | Producer          | Consumer(s)                               |
|---------------------|-------------------|-------------------------------------------|
| `user-events`       | auth-service      | notification-service, recommender-service |
| `content-events`    | content-service   | notification-service, recommender-service |
| `media-events`      | media-service     | content-service                           |

### 3.3 Redis (cache dùng chung)

Dùng prefix key để tránh xung đột giữa các service:

```
user:{userId}          → auth-service
content:{contentId}    → content-service
session:{token}        → api-gateway
```

---

## 4. Hướng dẫn chạy project

### Bước 1 — Chuẩn bị môi trường

- Docker Desktop đang chạy
- Clone repo về máy
- Copy file cấu hình:

```bash
cp .env.example .env
# Chỉnh sửa .env nếu cần (DB password, secret key, v.v.)
```

### Bước 2 — Khởi động hạ tầng (DB, Kafka, Redis...)

```bash
cd infra
docker compose up -d
```

### Bước 3 — Build & chạy từng service

```bash
# Build toàn bộ project (từ thư mục gốc)
mvn clean package -DskipTests

# Hoặc chạy từng service riêng lẻ
cd media-service
mvn spring-boot:run
```

### Bước 4 — Kiểm tra service đang chạy

```bash
# Xem tất cả container
docker compose ps

# Kiểm tra health
curl http://localhost:8080/actuator/health   # API Gateway
curl http://localhost:8085/actuator/health   # Media Service
```

---

## 5. Quy trình làm việc

### 5.1 Git Workflow

```
main          ← production (chỉ merge qua PR đã review)
  └── develop ← branch tích hợp chính
        ├── feature/<tên-feature>   ← phát triển tính năng mới
        └── fix/<tên-bug>           ← sửa lỗi
```

- **Không push thẳng** vào `main` hoặc `develop`.
- Tạo **Pull Request** vào `develop`, cần ít nhất **1 người review** trước khi merge.
- Đặt tên branch: `feature/post-create-api`, `fix/auth-refresh-token-bug`.
- Commit message:
  - `feat: thêm API upload media`
  - `fix: sửa lỗi UserContext khi thiếu header`
  - `refactor: tách MediaService thành module riêng`
  - `docs: cập nhật README`

### 5.2 Thêm Migration Database (Flyway)

Mỗi service quản lý schema riêng. File migration đặt tại:

```
<service-name>/src/main/resources/db/migration/
```

Đặt tên file theo format Flyway:

```
V1__create_media_schema.sql
V2__add_thumbnail_column.sql
```

> ⚠️ **Không sửa** file migration đã được commit. Nếu cần thay đổi, tạo file migration mới.

### 5.3 Code Convention

| Mục               | Quy tắc                                                                   |
|-------------------|---------------------------------------------------------------------------|
| Đặt tên class     | `PascalCase` — `MediaService`, `UserContext`                              |
| Đặt tên method    | `camelCase` — `getCurrentUserId()`, `upload()`                            |
| Đặt tên biến      | `camelCase` — `currentUserId`, `mediaFile`                                |
| Hằng số           | `UPPER_SNAKE_CASE` — `HEADER_USER_ID`, `MAX_FILE_SIZE`                    |
| Package           | `com.example.<service>.<layer>` — `com.example.media.util`                |
| Comment           | Tiếng Việt hoặc tiếng Anh đều được, miễn là rõ ràng                      |

### 5.4 Cấu trúc package chuẩn (mỗi service)

```
com.example.<service>/
├── controller/    ← REST endpoints
├── service/       ← business logic
├── repository/    ← JPA repositories
├── entity/        ← JPA entities (ánh xạ bảng DB)
├── dto/           ← Data Transfer Objects (request/response)
├── exception/     ← custom exceptions (bao gồm UnauthorizedException)
├── config/        ← Spring configuration
├── util/          ← UserContext và các helper khác  ⬅ quan trọng
└── event/         ← Kafka producer/consumer
```

### 5.5 DOs & DON'Ts

| ❌ Không nên                                       | ✅ Nên làm                                                          |
|---------------------------------------------------|--------------------------------------------------------------------|
| Dùng `@RequestHeader("X-User-Id")` trong method  | Gọi `UserContext.getCurrentUserId()` trong body method             |
| Tự verify JWT trong downstream service            | Tin tưởng header `X-User-Id` do Gateway đã xác thực               |
| Gọi database của service khác trực tiếp           | Gọi qua REST API hoặc nhận event qua Kafka                         |
| Hardcode URL / IP trong code                      | Dùng service name Docker + config trong `application.yml`          |
| Push code chưa test lên `develop`                 | Viết unit test, chạy test trước khi tạo PR                         |
| Để thông tin nhạy cảm (password, secret) trong code | Dùng biến môi trường trong `.env`                               |

---

## 6. Tài liệu tham khảo thêm

| Tài liệu                                                        | Nội dung                                |
|-----------------------------------------------------------------|-----------------------------------------|
| `docs/run/API_GATEWAY_AND_SERVICE_COMMUNICATION.md`            | Chi tiết giao tiếp inter-service        |
| `docs/run/DOCKER_SETUP.md`                                     | Hướng dẫn Docker đầy đủ                |
| `docs/database/README.md`                                      | Hướng dẫn Flyway migration              |
| `docs/services/auth/LOGIN_REGISTER_SERVICE.md`                 | Luồng đăng nhập / đăng ký              |
| `docs/services/auth/RBAC_ROLE_BASED_ACCESS_CONTROL.md`        | Phân quyền theo role                    |
| `docs/services/auth/ADMIN_DASHBOARD_AND_MANAGEMENT.md`         | Hướng dẫn tích hợp Admin Dashboard     |
| `infra/docker-compose.yml`                                     | Cấu hình toàn bộ hạ tầng               |

---

> 💬 **Có thắc mắc?** Tạo issue trên Git hoặc nhắn trực tiếp cho team lead.