# Hướng Dẫn Kiến Trúc & Luồng Xử Lý Code Dành Cho Developer (Developer Guide)

> **Dành cho:** Tất cả thành viên lập trình viên trong team dự án **Snapgram Server**.  
> **Mục tiêu:** Giúp bạn nhanh chóng đọc hiểu mã nguồn, nắm bắt luồng thực thi từ Controller đến Database/Kafka/Redis, và dễ dàng tự tin phát triển tính năng mới đúng chuẩn kiến trúc hệ thống.

---

## Mục lục

1. [Tổng quan Kiến trúc Hệ thống](#1-tổng-quan-kiến-trúc-hệ-thống)
2. [Cấu trúc Thư mục Chuẩn của Microservice & Quy tắc Đọc Code](#2-cấu-trúc-thư-mục-chuẩn-của-microservice--quy-tắc-đọc-code)
3. [Cơ chế Phân luồng DTO theo Chức năng (Feature-based DTOs)](#3-cơ-chế-phân-luồng-dto-theo-chức-năng-feature-based-dtos)
4. [Các Luồng Xử Lý Điển Hình (End-to-End Execution Flows)](#4-các-luồng-xử-lý-điển-hình-end-to-end-execution-flows)
   - [Luồng 1: Đồng bộ REST CRUD (Ví dụ: Tạo bài viết mới)](#luồng-1-đồng-bộ-rest-crud-ví-dụ-tạo-bài-viết-mới)
   - [Luồng 2: Xác thực & Đăng nhập (Auth Flow)](#luồng-2-xác-thực--đăng-nhập-auth-flow)
   - [Luồng 3: Bất đồng bộ Event-Driven & Transactional Outbox (Thanh toán & Cấp quyền)](#luồng-3-bất-đồng-bộ-event-driven--transactional-outbox-thanh-toán--cấp-quyền)
   - [Luồng 4: Thông báo Realtime WebSocket & FCM Push Fallback](#luồng-4-thông-báo-realtime-websocket--fcm-push-fallback)
5. [Bản đồ Trách nhiệm của 8 Microservices](#5-bản-đồ-trách-nhiệm-của-8-microservices)
6. [Quy trình Phát triển Tính năng Mới (Developer Workflow)](#6-quy-trình-phát-triển-tính-năng-mới-developer-workflow)
7. [Cấu hình Bật/Tắt Swagger API Docs (Dev vs Production)](#7-cấu-hình-bậttắt-swagger-api-docs-dev-vs-production)

---

## 1. Tổng quan Kiến trúc Hệ thống

Snapgram Server được xây dựng theo kiến trúc **Microservices Gateway-First**:

```text
┌─────────────────────────────────────────────────────────────────┐
│                     Client (Web / Mobile App)                   │
└────────────────────────────────┬────────────────────────────────┘
                                 │ HTTP / WebSocket Request
                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API GATEWAY (Port 8080)                      │
│  - Cửa ngõ DUY NHẤT tiếp nhận mọi request từ ngoài               │
│  - Xác thực JWT tập trung qua Auth Service                      │
│  - Trích xuất thông tin người dùng và inject headers:           │
│      X-User-Id: <UUID>                                          │
│      X-User-Roles: USER,ADMIN                                   │
│  - Rate Limiting, Circuit Breaker, CORS, Health Check           │
└────────────────────────────────┬────────────────────────────────┘
                                 │ Forward request (kèm headers nội bộ)
     ┌───────────────────────────┼───────────────────────────┐
     ▼                           ▼                           ▼
┌───────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ auth-service  │       │ content-service │       │  chat-service   │
│  (Port 8081)  │       │   (Port 8084)   │       │   (Port 8083)   │
└───────┬───────┘       └────────┬────────┘       └────────┬────────┘
        │                        │                         │
        └────────────────────────┼─────────────────────────┘
                                 │ Kafka Topics / Redis Cache
                                 ▼
┌───────────────┐       ┌─────────────────┐       ┌─────────────────┐
│ media-service │       │payment-service  │       │notification-svc│
│  (Port 8085)  │       │   (Port 8087)   │       │   (Port 8086)   │
└───────────────┘       └─────────────────┘       └─────────────────┘
                                 ▲
                        ┌────────┴────────┐
                        │recommender-svc  │
                        │   (Port 8090)   │
                        └─────────────────┘
```

### ⚡ Nguyên tắc Cốt lõi: Zero-JWT ở Downstream
1. **API Gateway xác minh token JWT**: Sau khi token hợp lệ, Gateway inject `X-User-Id` và `X-User-Roles` vào header của request forward nội bộ.
2. **Các service nội bộ KHÔNG parse lại JWT**:
   - Sử dụng class tiện ích `UserContext.getCurrentUserId()` để lấy ID của người dùng gọi API.
   - Sử dụng `UserContext.isAdmin()` hoặc `UserContext.requireAdmin()` để kiểm tra quyền Admin.
   - Giúp các service chạy cực nhanh, không phụ thuộc thư viện JWT ở mọi nơi và dễ dàng viết Unit Test (chỉ cần mock header).

---

## 2. Cấu trúc Thư mục Chuẩn của Microservice & Quy tắc Đọc Code

Mỗi microservice trong Snapgram tuân thủ mô hình **Layered Architecture (Kiến trúc phân tầng)** chuẩn:

```text
com.example.<service>/
├── controller/     ← 1. Tầng Giao tiếp HTTP (API Entrypoint)
├── dto/            ← 2. Tầng Truyền tải Dữ liệu (Phân loại theo Chức năng)
│   ├── <chức_năng_A>/
│   │   ├── ...Request.java
│   │   └── ...Response.java
│   └── common/
├── service/        ← 3. Tầng Nghiệp vụ Lõi (Business Logic)
├── repository/     ← 4. Tầng Truy xuất Dữ liệu (Spring Data JPA)
├── entity/         ← 5. Tầng Thực thể Database (JPA Entities)
├── event/          ← 6. Tầng Sự kiện Bất đồng bộ (Kafka Event / DTO)
├── config/         ← 7. Cấu hình Hạ tầng (Security, Kafka, Redis, Swagger)
├── util/           ← 8. Tiện ích Dùng chung (UserContext)
└── exception/      ← 9. Xử lý Lỗi Tập trung (GlobalExceptionHandler)
```

### 📖 Trình tự Đọc Code khi Tìm hiểu một Tính năng
Khi muốn hiểu một API làm gì, hãy đọc theo trình tự:
1. **Bắt đầu tại `Controller`**: Xem endpoint URL (`@PostMapping`, `@GetMapping`), dữ liệu nhận vào (Request DTO có `@Valid`) và kiểm tra quyền (`UserContext.getCurrentUserId()`).
2. **Chuyển sang `Service`**: Nơi chứa toàn bộ quyết định nghiệp vụ, tính toán, kiểm tra hợp lệ, quản lý transaction `@Transactional`.
3. **Xem `Repository` & `Entity`**: Xem câu lệnh truy vấn database, bảng dữ liệu nào đang được đọc/ghi.
4. **Kiểm tra `Kafka Event / Outbox`**: Xem sau khi lưu database thành công, service có phát sinh sự kiện nào bắn sang các service khác không.
5. **Kiểm tra `Exception`**: Xem nếu gặp lỗi nghiệp vụ, hệ thống ném exception gì và `GlobalExceptionHandler` format JSON trả về client ra sao.

---

## 3. Cơ chế Phân luồng DTO theo Chức năng (Feature-based DTOs)

Để tránh tình trạng hàng chục file DTO dồn cục trong một thư mục gây rối mắt, dự án tổ chức DTO **theo từng nhóm chức năng nghiệp vụ**:

### Ví dụ 1: `auth-service`
```text
com.example.auth.dto/
├── auth/           ← Đăng nhập, đăng ký, 2FA, đổi/quên mật khẩu, Facebook auth
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── AuthResponse.java
│   └── ...
├── user/           ← Hồ sơ cá nhân, ghi chú (notes), quyền riêng tư, chặn user
│   ├── UpdateProfileRequest.java
│   ├── UserProfileResponse.java
│   └── ...
├── admin/          ← Quản lý người dùng của admin, ban user, dashboard thống kê
│   ├── AdminDashboardResponse.java
│   ├── AdminUserResponse.java
│   └── ...
├── rbac/           ← Phân quyền động, quản lý Role & Permission
│   ├── CreateRoleRequest.java
│   ├── RoleDetailResponse.java
│   └── ...
└── common/         ← DTO dùng chung
    └── ErrorResponse.java
```

### Ví dụ 2: `content-service`
```text
com.example.content.dto/
├── post/           ← Tạo bài viết, sửa bài viết, media bài viết, lưu bài viết
│   ├── CreatePostRequest.java
│   ├── UpdatePostRequest.java
│   ├── PostResponse.java
│   └── ...
├── story/          ← Story 24h, người xem story, Highlights tin nổi bật
│   ├── CreateStoryRequest.java
│   ├── StoryResponse.java
│   └── ...
├── comment/        ← Bình luận bài viết
├── follow/         ← Theo dõi, danh sách follower/following
├── interaction/    ← Thả tim, like, tương tác
├── hashtag/        ← Khám phá bài viết theo hashtag
├── report/         ← Báo cáo vi phạm nội dung
└── ticket/         ← Ticket hỗ trợ kỹ thuật
```

### 💡 Quy tắc Vàng về DTO:
- **KHÔNG BAO GIỜ** trả trực tiếp JPA Entity (`Post`, `User`, `Comment`) ra ngoài Controller! Luôn map sang `Response` DTO tương ứng. Điều này ngăn chặn:
  - Rò rỉ thông tin nhạy cảm (như `passwordHash`, cấu trúc khóa ngoại).
  - Lỗi vòng lặp vô tận Jackson Serialization (`JsonMappingException` do quan hệ 2 chiều).
  - Tình trạng N+1 query ngầm do Lazy Loading khi chuyển đổi JSON.

---

## 4. Các Luồng Xử Lý Điển Hình (End-to-End Execution Flows)

### Luồng 1: Đồng bộ REST CRUD (Ví dụ: Tạo bài viết mới)

**Mô tả:** Người dùng gửi yêu cầu đăng bài viết mới kèm nội dung text và danh sách ảnh/video.

```text
Client ──[POST /api/posts]──> API Gateway ──[Inject Header]──> PostController
                                                                    │
   ┌────────────────────────────────────────────────────────────────┘
   ▼
PostController
   │  1. Đọc userId = UserContext.getCurrentUserId()
   │  2. Gọi postService.createPost(userId, createPostRequest)
   ▼
PostService
   │  3. Validate định dạng, độ dài caption, số lượng media
   │  4. Tạo entity Post (id = UUID.randomUUID())
   │  5. postRepository.save(post)  ──> [PostgreSQL: content_posts]
   │  6. Bắn Kafka event: "content.post-created" ──> [Kafka Broker]
   │     (Để recommender-service cập nhật feed và notification-service báo follower)
   │  7. Map entity sang PostResponse
   ▼
PostController ──[201 Created: PostResponse]──> Client
```

---

### Luồng 2: Xác thực & Đăng nhập (Auth Flow)

**Mô tả:** Người dùng nhập username/email và mật khẩu để nhận Access Token.

```text
Client ──[POST /api/auth/login]──> API Gateway ──(Public Route)──> AuthController
                                                                        │
   ┌────────────────────────────────────────────────────────────────────┘
   ▼
AuthController
   │  1. Nhận LoginRequest (@Valid: email/username, password)
   │  2. Gọi authService.login(request)
   ▼
AuthService
   │  3. user = userRepository.findByEmail(email)
   │  4. Kiểm tra tài khoản có bị khóa hoặc bị ban không
   │  5. passwordEncoder.matches(rawPassword, user.getPasswordHash())
   │     - Nếu sai: Tăng số lần thử sai, khóa tạm nếu vượt ngưỡng.
   │     - Nếu đúng: Reset bộ đếm thử sai.
   │  6. Nếu bật 2FA: Trả về { requiresTwoFa: true, tempToken: "..." }
   │  7. Nếu không 2FA:
   │     - accessToken = jwtTokenProvider.generateAccessToken(user)
   │     - refreshToken = refreshTokenService.create(user)
   │     - Trả về AuthResponse { accessToken, refreshToken, user: UserSummary }
   ▼
AuthController ──[200 OK: AuthResponse]──> Client
```

---

### Luồng 3: Bất đồng bộ Event-Driven & Transactional Outbox (Thanh toán & Cấp quyền)

**Mô tả:** Người dùng thanh toán mua dấu tích xanh (Blue Tick) qua MoMo hoặc VNPay. Đây là mô hình đảm bảo tính toàn vẹn tuyệt đối (Eventual Consistency) qua **Transactional Outbox Pattern**:

```text
1. Cổng thanh toán (MoMo/VNPay) gửi Webhook (IPN) về payment-service:
   POST /api/payments/momo/ipn ──> MomoCallbackController ──> PaymentService

2. Trong PaymentService (@Transactional):
   - Cập nhật trạng thái đơn hàng: status = 'SUCCESS' trong bảng [payment_orders]
   - Ghi một bản ghi sự kiện vào bảng [payment_push_outbox] trong CÙNG transaction:
     { topic: "payment.completed", payload: { userId, productCode: "BLUE_TICK", ... } }
   - Kết thúc transaction DB (Đảm bảo 100%: nếu lưu đơn thì chắc chắn có outbox)

3. PaymentOutboxDispatcher (Chạy định kỳ nền @Scheduled mỗi 1-2 giây):
   - Quét bảng [payment_push_outbox] tìm các bản ghi PENDING
   - Gửi message vào Kafka Topic: "payment.completed"
   - Nhận Kafka ACK thành công ➔ Đánh dấu outbox: status = 'SENT'

4. Bên auth-service (PaymentCompletedConsumer):
   - Lắng nghe Kafka Topic: "payment.completed"
   - Nhận event ➔ Kiểm tra chống trùng lặp (Idempotency)
   - Cập nhật tài khoản người dùng: cấp quyền Blue Tick, gia hạn 30 ngày
   - Commit Kafka Offset thành công
```

*Lợi ích:* Nếu Kafka bị sập đúng lúc thanh toán, outbox vẫn nằm an toàn trong database và sẽ tự động gửi lại ngay khi Kafka online trở lại (Zero Data Loss).

---

### Luồng 4: Thông báo Realtime WebSocket & FCM Push Fallback

**Mô tả:** Khi một người dùng được ai đó theo dõi hoặc tag vào bình luận:

```text
Kafka Topic ("notification.events")
   │
   ▼
NotificationConsumerService (notification-service)
   │  1. Kiểm tra bảng [notification_processed_events] chống nhận tin trùng lặp
   │  2. Lưu bản ghi thông báo mới vào bảng [notifications]
   │  3. Kiểm tra người nhận có đang online không (Redis Presence / WebSocket Session):
   │
   ├─► [NẾU USER ĐANG ONLINE]:
   │      WebSocketNotificationService đẩy trực tiếp thông báo qua kết nối
   │      WebSocket (/ws/notifications) ➔ Chuông thông báo trên Web/App nảy số ngay.
   │
   └─► [NẾU USER OFFLINE]:
          Lưu thông báo vào bảng [notification_push_outbox]
          NotificationOutboxDispatcher quét gửi Firebase Cloud Messaging (FCM)
          đến thiết bị điện thoại của user qua Device Token đã đăng ký.
```

---

## 5. Bản đồ Trách nhiệm của 8 Microservices

| Service | Port ngoài | Host Docker | Database | Nhiệm vụ chính |
| :--- | :---: | :--- | :--- | :--- |
| **`api-gateway`** | `8080` | `api-gateway` | *(Redis)* | Cửa ngõ API duy nhất, xác thực JWT, định tuyến, rate limiting, kiểm tra sức khỏe hệ thống (`/api/admin/health`). |
| **`auth-service`** | `8081` | `auth-service` | `postgres-auth` (5432) | Đăng ký, đăng nhập, cấp refresh token, 2FA Google Authenticator, phân quyền động RBAC, hồ sơ cá nhân, chặn user. |
| **`chat-service`** | `8083` | `chat-service` | `postgres-chat` (5433) | Nhắn tin 1-1, WebSocket chat realtime (`/ws/chat`), lịch sử hội thoại, trạng thái đang nhập (typing), thu hồi tin nhắn. |
| **`content-service`** | `8084` | `content-service` | `postgres-content` (5435) | Bài viết (post), tin 24h (story), tin nổi bật (highlights), bình luận, follow/unfollow, hashtag, báo cáo vi phạm, ticket hỗ trợ. |
| **`media-service`** | `8085` | `media-service` | `postgres-media` (5437) | Tiếp nhận file ảnh/video, lưu trữ MinIO S3, cấp presigned upload URL, tích hợp AI gợi ý caption & hashtag tự động. |
| **`notification-service`** | `8086` | `notification-service` | `postgres-notification` (5438) | Trung tâm nhận sự kiện thông báo từ Kafka, đẩy realtime qua WebSocket, outbox gửi push notification FCM tới mobile. |
| **`payment-service`** | `8087` | `payment-service` | `postgres-payment` (5439) | Tích hợp cổng thanh toán MoMo và VNPay, xử lý IPN webhook, Transactional Outbox phát sự kiện thanh toán thành công. |
| **`recommender-service`** | `8090` | `recommender-service` | `postgres-recommender` (5436) | CQRS Read-model, thuật toán đề xuất bảng tin (feed), xếp hạng bài viết theo tương tác, gợi ý tài khoản nên theo dõi. |

---

## 6. Quy trình Phát triển Tính năng Mới (Developer Workflow)

Khi bạn được giao một task mới (ví dụ: *"Thêm tính năng Bookmark bài viết"*), hãy làm theo các bước chuẩn sau:

```text
[Bước 1: Database Migration]
   └─► Viết file SQL mới trong: src/main/resources/db/migration/V<Next_Version>__<name>.sql
       (Tuyệt đối KHÔNG sửa file migration cũ đã commit!)

[Bước 2: JPA Entity]
   └─► Tạo Entity class trong package: entity/ (khớp tên bảng và trường với migration).

[Bước 3: Repository]
   └─► Tạo interface trong package: repository/ kế thừa JpaRepository<Entity, UUID>.

[Bước 4: DTOs]
   └─► Tạo Request DTO và Response DTO trong package: dto/<tên_chức_năng>/
       Ví dụ: dto/post/BookmarkRequest.java, dto/post/BookmarkResponse.java.

[Bước 5: Service & Unit Test]
   └─► Viết logic nghiệp vụ trong: service/BookmarkService.java (@Transactional nếu có ghi DB).
   └─► Viết Unit Test tương ứng trong src/test/java/... để đảm bảo test coverage.

[Bước 6: Controller]
   └─► Tạo REST endpoint trong controller/: BookmarkController.java
   └─► Đọc user ID: UUID userId = UserContext.getCurrentUserId();
   └─► Nhận @Valid BookmarkRequest và trả về ResponseEntity<BookmarkResponse>.

[Bước 7: API Gateway Route (Nếu cần)]
   └─► Nếu endpoint có prefix mới, khai báo route tương ứng trong api-gateway/src/main/resources/application.yml.

[Bước 8: Kiểm thử]
   └─► Chạy `mvn test -pl <service-name>` để xác nhận toàn bộ test PASS.
   └─► Mở Swagger UI (`http://localhost:<port>/swagger-ui.html`) kiểm tra trực tiếp.
```

---

## 7. Cấu hình Bật/Tắt Swagger API Docs (Dev vs Production)

Swagger UI và OpenAPI Docs là công cụ hỗ trợ tuyệt vời trong quá trình phát triển (Local & Staging), nhưng khi triển khai lên môi trường **Production**, tài liệu API nội bộ **bắt buộc phải tắt** để tránh lộ cấu trúc dữ liệu cho kẻ tấn công (Information Disclosure Vulnerability).

### Cách Bật/Tắt thông qua Biến Môi Trường

Hệ thống Snapgram đã tích hợp sẵn cờ điều khiển `SWAGGER_ENABLED` trên toàn bộ các service:

- **Môi trường Local / Dev (Mặc định BẬT):**
  Trong file `.env`:
  ```bash
  SWAGGER_ENABLED=true
  ```
  Truy cập tài liệu API:
  - Auth Service: `http://localhost:8081/swagger-ui.html`
  - Content Service: `http://localhost:8084/swagger-ui.html`
  - Media Service: `http://localhost:8085/swagger-ui.html`
  - Payment Service: `http://localhost:8087/swagger-ui.html`
  - Recommender Service: `http://localhost:8090/swagger-ui.html`

- **Môi trường Production (TẮT HOÀN TOÀN):**
  Khi deploy production, cấu hình biến môi trường:
  ```bash
  SWAGGER_ENABLED=false
  ```
  Khi giá trị là `false`:
  1. Toàn bộ endpoint `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**` bị ngắt hoàn toàn (trả về 404 Not Found).
  2. Annotation `@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")` trên `SwaggerConfig` sẽ ngăn Spring Boot quét và khởi tạo các bean OpenAPI ➔ **Tiết kiệm RAM và tăng tốc khởi động container**.
