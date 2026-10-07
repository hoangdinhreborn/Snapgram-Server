# Hướng Dẫn Tích Hợp Admin Dashboard & Quản Trị Hệ Thống Snapgram

Tài liệu này cung cấp toàn bộ kiến trúc, danh sách API và hướng dẫn chi tiết để xây dựng trang **Admin Dashboard / Admin Portal** cho hệ thống Snapgram.

---

## 1. Cơ Chế Phân Quyền Admin (RBAC)

- **Role quy định**: Mặc định người dùng khi đăng ký chỉ có role `USER`. Chỉ những tài khoản có role `ADMIN` mới được phép gọi các API quản trị và dashboard.
- **Bảo mật**:
  - Tại **API Gateway**: Token JWT được kiểm tra và inject các header downstream (`X-User-Id`, `X-Username`, `X-User-Roles`).
  - Tại **Auth Service**: Toàn bộ các endpoint bắt đầu bằng `/api/auth/admin/**` đều được Spring Security bảo vệ bằng `.hasRole("ADMIN")`. Request từ user thông thường sẽ nhận ngay mã lỗi **HTTP 403 Forbidden**.
  - Tại **Content Service**: Được bảo vệ bằng `UserContext.requireAdmin()`.

---

## 2. Cách Khởi Tạo Tài Khoản Admin Đầu Tiên

Khi mới cài đặt hệ thống hoặc database trắng, bạn có thể cấp quyền Admin cho tài khoản của mình theo 1 trong 2 cách sau:

### Cách 1: Gọi API Bootstrap (Khuyến nghị - Không cần mở DB)
Gửi request với `secretKey` mặc định:
```http
POST /api/auth/admin/bootstrap
Host: localhost:8080 (hoặc auth-service port 8081)
Content-Type: application/json

{
  "usernameOrEmail": "your_username",
  "secretKey": "snapgram-admin-bootstrap-secret"
}
```

### Cách 2: Gõ SQL trực tiếp vào PostgreSQL (`postgres-auth`)
```sql
-- 1. Lấy user_id
SELECT id, username, email FROM auth_users WHERE username = 'your_username';

-- 2. Thêm role ADMIN
INSERT INTO auth_user_roles (id, user_id, role, created_at)
VALUES (gen_random_uuid(), '<user_id_o_buoc_1>', 'ADMIN', NOW());
```

> ⚠️ **BƯỚC BẮT BUỘC SAU KHI CẤP QUYỀN**:
> Sau khi cấp quyền Admin, **bắt buộc bạn phải đăng nhập lại** (`POST /api/auth/login`) để nhận Access Token mới có chứa claim `"roles": ["USER", "ADMIN"]`.

---

## 3. API Thống Kê Tổng Quan (Dashboard Stats)

API này tổng hợp các chỉ số quan trọng theo thời gian thực (real-time) để hiển thị lên trang chủ của Admin Dashboard.

### `GET /api/auth/admin/dashboard/stats`
- **Yêu cầu Header**: `Authorization: Bearer <admin_token>`
- **Response**: `200 OK`

```json
{
  "totalUsers": 1250,
  "activeUsers": 1242,
  "bannedUsers": 8,
  "newUsersToday": 15,
  "newUsersThisWeek": 85,
  "emailVerifiedUsers": 950,
  "twoFaEnabledUsers": 420,
  "recentUsers": [
    {
      "id": "8b5f3a02-c923-41bb-b054-94c34a26e820",
      "username": "hoang_dev",
      "email": "hoang@example.com",
      "displayName": "Hoàng Dev",
      "avatarUrl": "https://cdn.snapgram.com/avatars/hoang.png",
      "privateAccount": false,
      "emailVerified": true,
      "twoFaEnabled": true,
      "banned": false,
      "bannedAt": null,
      "banReason": null,
      "lastSeenAt": "2026-09-28T08:20:00Z",
      "createdAt": "2026-09-28T08:00:00Z",
      "roles": ["USER", "ADMIN"]
    }
  ]
}
```

### Hướng Dẫn Thiết Kế UI Frontend Từ Response:
| Thành phần giao diện (UI Widget) | Trường dữ liệu sử dụng | Mô tả hiển thị |
|---|---|---|
| **Thẻ Tổng Users** | `totalUsers`, `activeUsers`, `bannedUsers` | Hiển thị tổng số tài khoản, kèm badge phân loại Active / Banned |
| **Thẻ Tăng Trưởng Hôm Nay** | `newUsersToday` | Số lượng tài khoản đăng ký mới từ 00:00 UTC hôm nay |
| **Thẻ Tăng Trưởng Tuần Này** | `newUsersThisWeek` | Số lượng tài khoản đăng ký trong 7 ngày gần nhất |
| **Thẻ Tỷ Lệ Bảo Mật (2FA)** | `twoFaEnabledUsers` / `totalUsers` | Tỷ lệ người dùng đã kích hoạt xác thực 2 bước |
| **Thẻ Xác Thực Email** | `emailVerifiedUsers` / `totalUsers` | Tỷ lệ tài khoản chính chủ đã kích hoạt email |
| **Bảng Đăng Ký Mới Nhất** | `recentUsers` | Bảng danh sách 5 user mới đăng ký kèm avatar, trạng thái và nút hành động |

---

## 4. Danh Sách API Quản Trị Người Dùng (User Management)

### 4.1. Lấy danh sách người dùng (Phân trang)
```http
GET /api/auth/admin/users?page=0&size=20
Authorization: Bearer <admin_token>
```
- **Query params**:
  - `page`: Trang hiện tại (bắt đầu từ 0, mặc định `0`)
  - `size`: Số bản ghi mỗi trang (mặc định `20`, tối đa `50`)

### 4.2. Xem thông tin chi tiết một User
```http
GET /api/auth/admin/users/{userId}
Authorization: Bearer <admin_token>
```

### 4.3. Khóa tài khoản (Ban User)
Khóa tài khoản người dùng, ghi nhận lý do và **thu hồi (revoke) toàn bộ phiên đăng nhập (Refresh Token)** của người này:
```http
POST /api/auth/admin/users/{userId}/ban
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "reason": "Vi phạm chính sách cộng đồng, spam bài viết liên tục"
}
```
*Lưu ý: Admin không thể tự ban chính mình (Hệ thống trả về HTTP 400).*

### 4.4. Mở khóa tài khoản (Unban User)
```http
POST /api/auth/admin/users/{userId}/unban
Authorization: Bearer <admin_token>
```

### 4.5. Phân quyền / Gán Role cho User
Cấp thêm quyền Admin hoặc Moderator cho một tài khoản:
```http
POST /api/auth/admin/users/{userId}/roles
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "role": "ADMIN"
}
```

### 4.6. Thu hồi Role của User
```http
DELETE /api/auth/admin/users/{userId}/roles/ADMIN
Authorization: Bearer <admin_token>
```

---

## 5. Danh Sách API Kiểm Duyệt Báo Cáo (Content Moderation)

Nằm ở `content-service` (port 8084 hoặc gọi qua Gateway port 8080):

### 5.1. Xem danh sách báo cáo vi phạm
```http
GET /api/admin/reports?status=PENDING&page=0&size=20
Authorization: Bearer <admin_token>
```
- **Filter params**:
  - `status`: `PENDING`, `REVIEWED`, `RESOLVED`, `DISMISSED`
  - `targetType`: `POST`, `COMMENT`, `USER`

### 5.2. Xem chi tiết báo cáo
```http
GET /api/admin/reports/{reportId}
Authorization: Bearer <admin_token>
```

### 5.3. Duyệt báo cáo (Resolve - Xử lý nội dung vi phạm)
Khi Admin duyệt chấp thuận báo cáo:
- Nếu báo cáo về bài viết (`POST`): Trạng thái Post tự động chuyển sang `DELETED` (ẩn khỏi Newsfeed).
- Nếu báo cáo về bình luận (`COMMENT`): Comment vi phạm tự động bị xóa khỏi hệ thống.
- Báo cáo chuyển sang trạng thái `RESOLVED`.
```http
PATCH /api/admin/reports/{reportId}/resolve
Authorization: Bearer <admin_token>
```

### 5.4. Bác bỏ báo cáo (Dismiss - Báo cáo sai)
```http
PATCH /api/admin/reports/{reportId}/dismiss
Authorization: Bearer <admin_token>
```

---

## 6. Mẫu Tích Hợp Frontend (React / Axios / TypeScript)

```typescript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
});

// Gắn token Admin vào header
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Gọi dữ liệu Dashboard
export const fetchDashboardStats = async () => {
  try {
    const response = await apiClient.get('/api/auth/admin/dashboard/stats');
    return response.data;
  } catch (error: any) {
    if (error.response?.status === 403) {
      alert('Tài khoản của bạn không có quyền truy cập Admin!');
    }
    throw error;
  }
};

// Khóa tài khoản
export const banUser = async (userId: string, reason: string) => {
  const response = await apiClient.post(`/api/auth/admin/users/${userId}/ban`, {
    reason,
  });
  return response.data;
};
```
