# BÁO CÁO KHẢO SÁT ENDPOINTS & HẠ TẦNG SNAPGRAM (SEED DATA DEMO)

> Tài liệu khảo sát trực tiếp từ mã nguồn Java Spring Boot 4, Flyway migrations, và cấu hình Spring Cloud Gateway của dự án **Snapgram**.
> Ngày cập nhật: 11/2026

---

## 1. Tổng quan Kiến trúc Định tuyến & Xác thực

```
Client (Browser / Seed Script)
       │
       ▼  Port 8080 (http://localhost:8080)
┌────────────────────────────────────────────────────────┐
│  API GATEWAY                                           │
│  - Verify JWT (POST /api/auth/verify qua WebClient)    │
│  - Inject: X-User-Id, X-Username, X-User-Roles         │
│  - Rate Limit: 5000 req / 60s                          │
└──────────┬──────────┬──────────┬──────────┬────────────┘
           │          │          │          │
           ▼:8081     ▼:8085     ▼:8084     ▼:8090
        auth-srv   media-srv  content-srv recommender-srv
```

- **Mọi request từ script đi qua API Gateway** (`http://localhost:8080`).
- **Chỉ Gateway xác thực JWT**: Khi Bearer Token hợp lệ, Gateway inject headers `X-User-Id`, `X-Username`, `X-User-Roles`, `X-User-Permissions` xuống các downstream service.
- **Tất cả các service lưu trữ trên cơ sở dữ liệu Postgres độc lập**: Đồng bộ chéo qua Kafka events (`user.events`, `content.post-created`, `content.interaction`, `follow.events`, v.v.).

---

## 2. Bảng Tra Cứu Endpoints Chi Tiết

### 2.1 auth-service (Định tuyến: `/api/auth/**`, `/api/users/**`, `/api/admin/users/**`)

| Chức năng | Method | Path qua Gateway | Headers | Request Body | Response Fields Chính | Auth? |
|---|:---:|---|---|---|---|:---:|
| **Đăng ký** | `POST` | `/api/auth/register` | `Content-Type: application/json` | `{"username": "user1", "email": "user1@demo.local", "password": "...", "displayName": "User 1"}` | `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user: {id, username, email, displayName, role}` | Không |
| **Đăng nhập** | `POST` | `/api/auth/login` | `Content-Type: application/json` | `{"email": "user1@demo.local", "password": "..."}` | `accessToken`, `refreshToken`, `tokenType`, `expiresIn`, `user: {id, username, email, displayName, role}` | Không |
| **Profile của tôi** | `GET` | `/api/users/me` | `Authorization: Bearer <token>` | *None* | `UserProfileResponse`: `id`, `username`, `displayName`, `avatarUrl`, `bio`, `website`, `pronouns`, `category`, `role`, `privateAccount`, `verified`, `verifiedUntil`, `email`, `emailVerified` | Có |
| **Cập nhật Profile**| `PATCH`| `/api/users/me` | `Authorization: Bearer <token>` | `{"displayName": "...", "avatarUrl": "...", "bio": "...", "website": "https://...", "pronouns": "...", "category": "..."}` | `UserProfileResponse` đã cập nhật | Có |
| **Bật/Tắt Private** | `PUT` | `/api/users/me/privacy` | `Authorization: Bearer <token>` | `{"privateAccount": true}` | `UserProfileResponse` (`privateAccount`: boolean) | Có |
| **Tạo / Cập nhật Note** | `POST`| `/api/users/me/note` | `Authorization: Bearer <token>` | `{"content": "Trạng thái hôm nay..."}` (tối đa 60 ký tự) | `NoteResponse`: `id`, `userId`, `content`, `createdAt`, `expiresAt` (hết hạn sau 24h) | Có |
| **Xem Note người khác** | `GET`| `/api/users/{userId}/note` | `Authorization: Bearer <token>` | *None* | `NoteResponse` (hoặc 204 No Content nếu không có note) | Có |
| **Xem Profile người khác** | `GET` | `/api/users/{username}` | `Authorization: Bearer <token>` (tùy chọn) | *None* | `UserProfileResponse` (các trường bio/website ẩn nếu là private và chưa follow) | Tùy chọn |

#### Lưu ý Nghiệp vụ Auth:
1. **Login bằng Email, KHÔNG dùng Username**: DTO `LoginRequest` ràng buộc `@NotBlank @Email private String email`. Không hỗ trợ login trực tiếp bằng username.
2. **Kiểm tra `email_verified`**: Code `AuthService.login()` **không chặn** tài khoản chưa verify email (chỉ kiểm tra password, lock và ban). Tuy nhiên, SQL update được dùng để set `email_verified = true` nhằm chuẩn hóa profile demo và hiển thị tick xanh.
3. **Cloudflare Turnstile**: `TurnstileVerificationService` mặc định bật (`app.turnstile.enabled=true`). Khi chạy seed tự động trong môi trường dev, cần đặt biến môi trường `TURNSTILE_ENABLED=false` trong `.env` để không bị chặn bởi bot challenge.
4. **Redis Cache**: `auth-service` **không** cache User profile vào Redis (chỉ dùng Redis cho Rate Limiting Bucket4j và Token Blacklist `auth-service:blacklist:{jti}`). Vì vậy, không cần gọi xóa cache Redis sau khi can thiệp SQL.

---

### 2.2 media-service (Định tuyến: `/api/media/**`, `/api/admin/media/**`)

| Chức năng | Method | Path qua Gateway | Headers | Request Body | Response Fields Chính | Auth? |
|---|:---:|---|---|---|---|:---:|
| **Direct Upload** | `POST` | `/api/media/upload` | `Authorization: Bearer <token>`, `Content-Type: multipart/form-data` | Multipart Form: field name `file` | `MediaResponse`: `id`, `ownerId`, `url`, `thumbnailUrl`, `mimeType`, `sizeBytes`, `status`, `width`, `height`, `durationSec`, `createdAt` | Có |
| **Xem chi tiết Media** | `GET` | `/api/media/{id}` | `Authorization: Bearer <token>` | *None* | `MediaResponse` | Có |
| **Media của tôi** | `GET` | `/api/media/me` | `Authorization: Bearer <token>` | Query: `?page=0&size=20` | `Page<MediaResponse>` | Có |
| **Xóa Media** | `DELETE` | `/api/media/{id}` | `Authorization: Bearer <token>` | *None* | 204 No Content | Có |

#### Lưu ý Nghiệp vụ Media:
1. **Tính Đồng bộ (Synchronous)**: Direct upload (`POST /api/media/upload`) upload file lên MinIO, tự động sinh thumbnail (nếu là ảnh), lưu database với trạng thái `status = 'READY'` **ngay lập tức** và bắn Kafka event `media.uploaded`. Không cần bước xác nhận riêng biệt.
2. **MIME Types được hỗ trợ**: `image/jpeg`, `image/png`, `image/webp`, `image/gif`, `video/mp4`, `video/quicktime`, `video/webm`.
3. **Giới hạn dung lượng**: `spring.servlet.multipart.max-file-size: 50MB`, `max-request-size: 50MB`. Video demo (8 giây, ~2MB) nằm an toàn trong giới hạn.
4. **URL Media trả về**: Được build qua `StorageService.buildPublicUrl()` dùng tiền tố cấu hình `MINIO_PUBLIC_URL` (`http://localhost:9000/snapgram-media/users/{ownerId}/yyyy/MM/dd/{fileId}.ext`). Do container MinIO mở cổng host `9000:9000`, trình duyệt trên máy host mở được trực tiếp URL này.

---

### 2.3 content-service (Định tuyến: `/api/posts/**`, `/api/stories/**`, `/api/follows/**`, `/api/saved/**`, ...)

| Chức năng | Method | Path qua Gateway | Headers | Request Body | Response Fields Chính | Auth? |
|---|:---:|---|---|---|---|:---:|
| **Tạo Post** | `POST` | `/api/posts` | `Authorization: Bearer <token>` | JSON `CreatePostRequest` (xem chi tiết bên dưới) | `PostResponse`: `id`, `authorId`, `contentType`, `caption`, `mediaList`, `likeCount`, `commentCount`, `createdAt` | Có |
| **Chi tiết Post** | `GET` | `/api/posts/{id}` | `Authorization: Bearer <token>` (tùy chọn) | *None* | `PostResponse` | Tùy chọn |
| **Feed Cá nhân** | `GET` | `/api/posts/feed` | `Authorization: Bearer <token>` | Query: `?page=0&size=20` | `Page<PostResponse>` | Có |
| **Explore** | `GET` | `/api/posts/explore` | *None / Tùy chọn Bearer* | Query: `?page=0&size=20` | `Page<PostResponse>` | Tùy chọn |
| **Tương tác Post** | `POST` | `/api/posts/{postId}/interact` | `Authorization: Bearer <token>` | `{"type": "LIKE" \| "VIEW" \| "SKIP", "watchTimeRatio": 0.85}` | 204 No Content | Có |
| **Bỏ Like Post** | `DELETE` | `/api/posts/{postId}/interact/like` | `Authorization: Bearer <token>` | *None* | 204 No Content | Có |
| **Lưu Post (Save)** | `POST` | `/api/saved/{postId}` | `Authorization: Bearer <token>` | *None hoặc {}* | 201 Created | Có |
| **Bỏ Lưu Post** | `DELETE` | `/api/saved/{postId}` | `Authorization: Bearer <token>` | *None* | 204 No Content | Có |
| **Tạo Comment** | `POST` | `/api/posts/{postId}/comments` | `Authorization: Bearer <token>` | `{"content": "Hay quá!"}` | `CommentResponse`: `id`, `authorUsername`, `content`, `likeCount`, `replyCount` | Có |
| **Reply Comment** | `POST` | `/api/posts/{postId}/comments` | `Authorization: Bearer <token>` | `{"content": "Cảm ơn bạn", "parentCommentId": "<rootId>"}` | `CommentResponse`: `parentCommentId` tự động trỏ về root comment | Có |
| **Like Comment** | `POST` | `/api/posts/{postId}/comments/{commentId}/like` | `Authorization: Bearer <token>` | *None* | 204 No Content | Có |
| **Follow User** | `POST` | `/api/follows/{userId}` | `Authorization: Bearer <token>` | *None* | `FollowResponse`: `id`, `followerId`, `followingId`, `status` (`FOLLOWING` hoặc `PENDING`) | Có |
| **Danh sách Follow Request** | `GET` | `/api/follows/requests` | `Authorization: Bearer <token>` | Query: `?page=0&size=20` | `Page<FollowResponse>` | Có |
| **Duyệt Follow Request** | `POST` | `/api/follows/requests/{followId}/accept` | `Authorization: Bearer <token>` | *None* | `FollowResponse` (`status: "FOLLOWING"`) | Có |
| **Tạo Story** | `POST` | `/api/stories` | `Authorization: Bearer <token>` | `{"mediaUrl": "...", "mediaId": "...", "mediaType": "IMAGE"\|"VIDEO", "caption": "...", "visibility": "PUBLIC"}` | `StoryResponse`: `id`, `authorId`, `mediaUrl`, `mediaType`, `visibility`, `createdAt`, `expiresAt` | Có |
| **Feed Story** | `GET` | `/api/stories/feed` | `Authorization: Bearer <token>` | Query: `?page=0&size=20` | `Page<StoryResponse>` | Có |

#### Chi tiết Định dạng Post Body (`CreatePostRequest`):
```json
{
  "caption": "Mô tả bài viết #snapgram #vietnam",
  "visibility": "PUBLIC",
  "mediaList": [
    {
      "mediaId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "mediaUrl": "http://localhost:9000/snapgram-media/users/.../file1.jpg",
      "thumbnailUrl": "http://localhost:9000/snapgram-media/users/.../file1_thumb.jpg",
      "mediaType": "IMAGE",
      "sortOrder": 0,
      "width": 1080,
      "height": 1080
    },
    {
      "mediaId": "7ca85f64-5717-4562-b3fc-2c963f66afb2",
      "mediaUrl": "http://localhost:9000/snapgram-media/users/.../file2.mp4",
      "thumbnailUrl": "http://localhost:9000/snapgram-media/users/.../file2_thumb.jpg",
      "mediaType": "VIDEO",
      "sortOrder": 1,
      "durationSec": 8,
      "width": 1080,
      "height": 1920
    }
  ]
}
```
- **Hỗ trợ trộn ảnh + video**: `PostService` duyệt qua từng `PostMediaRequest` và gán `PostMediaType.IMAGE` hoặc `PostMediaType.VIDEO` độc lập.
- **Tự động nhận diện `contentType`**: Nếu client không truyền `contentType`, server tự suy luận:
  - Nếu không có media: `TEXT`
  - Nếu `mediaList.size() > 1`: `CAROUSEL`
  - Nếu `mediaList.size() == 1`: `IMAGE` hoặc `VIDEO` tùy theo item đầu tiên.
- **Flatten Comment Reply**: Nếu truyền `parentCommentId` là một reply khác, server tự động truy vết lên root comment (`resolvedParentId = targetParent.getParentCommentId() != null ? targetParent.getParentCommentId() : targetParent.getId()`).
- **Close Friends (Bạn thân)**: **Codebase hiện không có REST controller nào cho Close Friends** (chỉ có bảng/entity nội bộ `CloseFriend`). Do đó, script seed ghi nhận và bỏ qua mục này.

---

### 2.4 recommender-service (Định tuyến: `/api/feed/**`, `/api/recommendations/**`, `/api/admin/batch/**`)

| Chức năng | Method | Path qua Gateway | Headers | Request Body | Response Fields Chính | Auth? |
|---|:---:|---|---|---|---|:---:|
| **Kích hoạt Batch Job** | `POST` | `/api/admin/batch/jobs/{jobName}/run` | `Authorization: Bearer <ADMIN_TOKEN>` | *None* | `JobExecutionResponse`: `jobExecutionId`, `jobName`, `status` ("STARTED"), `createTime` | ADMIN |
| **Kiểm tra trạng thái Job** | `GET` | `/api/admin/batch/executions/{id}` | `Authorization: Bearer <ADMIN_TOKEN>` | *None* | `JobExecutionResponse`: `jobExecutionId`, `status` ("COMPLETED"), `exitCode`, `durationMs` | ADMIN |
| **Top Trending Posts** | `GET` | `/api/recommendations/trending` | *None / Bearer* | Query: `?limit=20` | `List<TrendingPostDto>` (đọc từ Redis cache hoặc DB) | Không |
| **Gợi ý Kết bạn** | `GET` | `/api/feed/suggested-users` | `Authorization: Bearer <token>` | Query: `?limit=10` | `List<SuggestedUserDto>` | Có |

#### Danh sách Spring Batch Jobs:
1. `trendingPostsJob`: Đọc từ projection view `recommender_post_view` và `recommender_interaction_view`, tính điểm số theo công thức thời gian + tương tác, ghi vào `recommender_trending_post` và cache Redis key `trending:posts`.
2. `userRecommendationJob`: Đọc mạng lưới follow từ `recommender_follow_view`, tính toán danh sách bạn chung Friend-of-Friends, ghi vào `recommender_user_recommendation` và cache Redis key `recommend:user:{userId}`.

---

## 3. Ngoại lệ SQL (Chỉ thực thi trên `snapgram-postgres-auth`)

Do hệ thống không có API public mở để verify email không cần gửi SMTP hoặc tự nâng cấp tài khoản admin ngoài bootstrap initializer, script thực thi qua `docker exec <container> psql`:

```sql
-- 1. Xác thực email cho toàn bộ user demo
UPDATE auth_users 
SET email_verified = true 
WHERE email LIKE '%@demo.local';

-- 2. Nâng tài khoản 'demo' lên ADMIN trong auth_users và auth_user_roles
UPDATE auth_users 
SET role = 'ADMIN' 
WHERE username = 'demo';

INSERT INTO auth_user_roles (id, user_id, role, created_at)
SELECT gen_random_uuid(), id, 'ADMIN', NOW()
FROM auth_users 
WHERE username = 'demo'
ON CONFLICT (user_id, role) DO NOTHING;

-- 3. Cấp tick xanh (verified badge) cho tài khoản 'demo'
UPDATE auth_users 
SET is_verified = true, 
    verified_until = NOW() + INTERVAL '365 days'
WHERE username = 'demo';
```
*(Ghi chú: auth-service không lưu cache profile user trên Redis nên không cần bước cache eviction).*

---

## 4. Bảng Tổng kết Mục Bỏ qua / Hạn chế Được Ghi nhận

| Mục yêu cầu ban đầu | Trạng thái | Lý do chi tiết |
|---|:---:|---|
| **Seed quan hệ Close Friends** | **BỎ QUA** | `content-service` chỉ có entity `CloseFriend.java` và repository, hoàn toàn chưa phát triển controller/service API. |
| **Login bằng Username** | **CHUYỂN SANG EMAIL** | `LoginRequest.java` bắt buộc trường `email`. Script dùng `<username>@demo.local`. |
| **Verify Email qua API** | **DÙNG SQL EXCEPTION** | API yêu cầu gửi email token qua SMTP thật. Dùng lệnh SQL cập nhật trực tiếp `email_verified = true` trên DB auth. |
| **Turnstile Protection** | **TẮT TRÊN MÔI TRƯỜNG DEV** | Đặt `TURNSTILE_ENABLED=false` trong `.env` để script seed không bị chặn bởi bot check. |
