# Hướng dẫn Tích hợp Frontend: Authentication (USER / ADMIN), httpOnly Cookie & Phân quyền

Tài liệu này cung cấp hướng dẫn đầy đủ, chi tiết cho đội ngũ Frontend (React, Vue, Next.js, v.v.) để tích hợp hệ thống xác thực Snapgram với cơ chế bảo mật **httpOnly Cookie**, phân quyền **USER / ADMIN**, chống brute-force và tự động làm mới token.

---

## 1. Nguyên tắc cốt lõi & Cấu trúc kết nối

1. **Cổng giao tiếp duy nhất (API Gateway):**
   - Frontend **CHỈ gọi qua API Gateway** (`http://localhost:8080` ở local hoặc domain production `https://snapgram.local`).
   - **TUYỆT ĐỐI KHÔNG** gọi trực tiếp port của `auth-service` (`:8081`).
2. **Bảo mật Token qua httpOnly Cookie:**
   - Server tự động thiết lập và hủy token thông qua HTTP response header `Set-Cookie`.
   - JavaScript ở frontend (`document.cookie`, `localStorage`, `sessionStorage`) **KHÔNG THỂ và KHÔNG CẦN** đọc hoặc lưu trữ access token / refresh token. Điều này ngăn chặn triệt để nguy cơ lộ token qua các lỗ hổng XSS.
3. **Cấu hình bắt buộc khi gửi Request (Crucial):**
   - Với mọi request từ frontend đến API Gateway, bắt buộc phải bật thuộc tính gửi kèm thông tin xác thực (credentials):
     - **Axios:** `withCredentials: true`
     - **Fetch API:** `credentials: 'include'`
4. **Phân quyền backend là chốt chặn duy nhất:**
   - Phân quyền ở frontend (ẩn/hiện menu, route guard) chỉ nhằm mục đích tối ưu hóa trải nghiệm người dùng (UX). Toàn bộ API nghiệp vụ và admin đều được bảo vệ nghiêm ngặt ở backend qua `hasAuthority("ROLE_ADMIN")`.

---

## 2. Danh sách API Endpoints

| Phương thức | Đường dẫn | Quyền truy cập | Mô tả |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Đăng ký người dùng thường (luôn là role `USER`) |
| `POST` | `/api/auth/login` | Public | Đăng nhập người dùng thường |
| `POST` | `/api/auth/admin/login` | Public | Đăng nhập dành riêng cho Quản trị viên (`ADMIN`) |
| `POST` | `/api/auth/login/2fa` | Public | Xác thực bước 2 (TOTP / Backup Code) khi tài khoản bật 2FA |
| `GET`  | `/api/auth/me` | Authenticated | Lấy thông tin user hiện tại và role (`USER` / `ADMIN`) |
| `POST` | `/api/auth/refresh` | Public / Cookie | Cấp lại access token mới khi access token cũ hết hạn |
| `POST` | `/api/auth/logout` | Authenticated | Đăng xuất, xóa toàn bộ cookie trên trình duyệt |

---

## 3. Chi tiết các luồng API

### 3.1. Đăng ký tài khoản (`POST /api/auth/register`)
- **Mục đích:** Đăng ký tài khoản người dùng mới.
- **Request Body:**
  ```json
  {
    "username": "john_doe",
    "email": "john@example.com",
    "password": "SecurePassword123",
    "displayName": "John Doe"
  }
  ```
- **Lưu ý quan trọng:** Không truyền trường `role`. Dù frontend có gửi `{"role": "ADMIN"}` thì backend vẫn bỏ qua và luôn gán tài khoản là `USER`.
- **Response (201 Created):**
  - Trình duyệt tự nhận 2 cookie: `access_token` (Path `/`) và `refresh_token` (Path `/api/auth`).
  - Response body chứa thông tin cơ bản:
    ```json
    {
      "user": {
        "id": "c0a80123-...",
        "username": "john_doe",
        "email": "john@example.com",
        "displayName": "John Doe",
        "role": "USER"
      }
    }
    ```

---

### 3.2. Đăng nhập Người dùng thường (`POST /api/auth/login`)
- **Mục đích:** Đăng nhập cho người dùng hệ thống.
- **Request Body:**
  ```json
  {
    "email": "john@example.com",
    "password": "SecurePassword123"
  }
  ```
- **Cookies được Server set tự động:**
  - `access_token`: `HttpOnly`, `SameSite=Lax`, `Path=/`, thời hạn 15 phút.
  - `refresh_token`: `HttpOnly`, `SameSite=Lax`, `Path=/api/auth`, thời hạn 30 ngày.
- **Trường hợp tài khoản bật 2FA:**
  - Nếu tài khoản đã kích hoạt 2FA, response trả về:
    ```json
    {
      "requiresTwoFa": true,
      "tempToken": "eyJhbGci..."
    }
    ```
  - Frontend chuyển sang màn hình nhập mã 2FA và gọi:
    `POST /api/auth/login/2fa` với body:
    ```json
    {
      "tempToken": "eyJhbGci...",
      "totpCode": "123456" // hoặc "backupCode": "ABC12-XYZ89"
    }
    ```

---

### 3.3. Đăng nhập Quản trị viên (`POST /api/auth/admin/login`)
- **Mục đích:** Cổng đăng nhập riêng biệt cho trang Admin Dashboard.
- **Request Body:**
  ```json
  {
    "email": "admin@snapgram.local",
    "password": "AdminPassword123"
  }
  ```
- **Quy tắc bảo mật:**
  - Chỉ cho phép tài khoản có `role == "ADMIN"`.
  - Nếu người dùng thường (`role == "USER"`) cố gắng đăng nhập tại đây (kể cả khi nhập đúng mật khẩu), server sẽ từ chối bằng lỗi **HTTP 401** chung: `Invalid email or password`.
  - Ngưỡng khóa brute-force nghiêm ngặt hơn: sai **3 lần** liên tiếp sẽ bị khóa tạm thời 15 phút.
- **Cookies được Server set tự động:**
  - `admin_access_token`: `HttpOnly`, `SameSite=Strict`, `Path=/`, thời hạn 30 phút.
  - `refresh_token`: `HttpOnly`, `SameSite=Strict`, `Path=/api/auth`, thời hạn 30 ngày.

---

### 3.4. Lấy thông tin User hiện tại (`GET /api/auth/me`)
- **Mục đích:** Xác định trạng thái đăng nhập và role của người dùng khi khởi động ứng dụng (app boot) hoặc khi người dùng F5/tải lại trang.
- **Request:** Không cần body, chỉ cần gọi kèm `withCredentials: true`.
- **Response thành công (200 OK):**
  ```json
  {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "username": "hoang_admin",
    "email": "admin@snapgram.local",
    "displayName": "Hoàng Quản Trị",
    "avatarUrl": "https://...",
    "role": "ADMIN", // Giá trị: "USER" hoặc "ADMIN"
    "emailVerified": true,
    "createdAt": "2026-10-01T08:00:00Z"
  }
  ```
- **Response chưa đăng nhập (401 Unauthorized):**
  ```json
  {
    "status": 401,
    "error": "Unauthorized",
    "message": "Invalid or expired token"
  }
  ```
  -> Frontend chuyển hướng người dùng về trang `/login`.

---

### 3.5. Làm mới Token (`POST /api/auth/refresh`)
- **Mục đích:** Cấp mới `access_token` khi access token hiện tại hết hạn.
- **Request:**
  - Phương thức: `POST /api/auth/refresh`
  - Body: `{}` (Rỗng)
  - Vì `refresh_token` đã được lưu trong httpOnly cookie với path `/api/auth`, trình duyệt sẽ tự động gửi kèm cookie này lên gateway và auth-service.
- **Response (200 OK):** Server set lại cookie `access_token` mới và xoay vòng (rotate) `refresh_token`.

---

### 3.6. Đăng xuất (`POST /api/auth/logout`)
- **Mục đích:** Đăng xuất tài khoản, hủy session trên server và xóa cookie trên trình duyệt.
- **Request:** `POST /api/auth/logout` với body `{}` và `withCredentials: true`.
- **Hoạt động:** Server trả về header `Set-Cookie` với `Max-Age=0` xóa sạch `access_token`, `admin_access_token`, `refresh_token`.
- **Xử lý frontend:** Xóa user state trong Context / Redux / Zustand và redirect về `/login`.

---

## 4. Cơ chế Chống Brute-force & Xử lý lỗi (Error Handling)

Hệ thống triển khai cơ chế chống dò mật khẩu và chống thu thập thông tin tài khoản (anti-enumeration):

1. **Thông báo lỗi đồng nhất (HTTP 401):**
   - Khi email không tồn tại trong hệ thống.
   - Khi nhập sai mật khẩu.
   - Khi tài khoản đang trong trạng thái bị khóa tạm thời.
   - Khi tài khoản role `USER` cố tình đăng nhập vào cổng `/api/auth/admin/login`.
   - **Tất cả các trường hợp trên đều trả về cùng một mã HTTP 401 với nội dung:**
     ```json
     {
       "status": 401,
       "error": "Unauthorized",
       "message": "Invalid email or password"
     }
     ```
2. **Quy tắc hiển thị thông báo lỗi trên UI:**
   - Frontend hiển thị thông báo thân thiện: *"Email hoặc mật khẩu không chính xác. Vui lòng thử lại."*
   - Không đưa ra thông báo phân biệt tài khoản có tồn tại hay không.
3. **Ngưỡng khóa tài khoản:**
   - Cổng người dùng thường (`/api/auth/login`): Khóa **15 phút** sau **5 lần** nhập sai.
   - Cổng quản trị viên (`/api/auth/admin/login`): Khóa **15 phút** sau **3 lần** nhập sai.
   - Khi hết thời gian khóa 15 phút, người dùng nhập đúng mật khẩu sẽ tự động đăng nhập bình thường và bộ đếm lỗi được reset về 0.

---

## 5. Code mẫu Frontend (Production Ready)

### 5.1. Cấu hình Axios Client (`apiClient.ts`)

File tạo HTTP Client hỗ trợ tự động gửi cookie và tự động Refresh Token khi gặp lỗi `401`:

```typescript
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

const GATEWAY_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const apiClient = axios.create({
  baseURL: GATEWAY_URL,
  withCredentials: true, // BẮT BUỘC: để trình duyệt tự gửi và nhận httpOnly Cookie
  headers: {
    'Content-Type': 'application/json',
  },
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: unknown) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve();
    }
  });
  failedQueue = [];
};

// Response Interceptor: Tự động refresh token khi gặp 401
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // Bỏ qua nếu lỗi 401 xảy ra ở các endpoint auth công khai
    if (
      !originalRequest ||
      originalRequest.url?.includes('/api/auth/login') ||
      originalRequest.url?.includes('/api/auth/register') ||
      originalRequest.url?.includes('/api/auth/refresh')
    ) {
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then(() => apiClient(originalRequest))
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // Gọi API refresh - trình duyệt tự động đính kèm cookie refresh_token
        await apiClient.post('/api/auth/refresh', {});
        processQueue(null);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError);
        // Refresh thất bại (hết hạn hoàn toàn) -> chuyển hướng về trang đăng nhập
        if (typeof window !== 'undefined') {
          window.location.href = '/login';
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);
```

---

### 5.2. Service xác thực (`authService.ts`)

```typescript
import { apiClient } from './apiClient';

export interface UserMe {
  id: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  displayName: string;
  avatarUrl?: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface LoginParams {
  email: string;
  password: string;
}

export interface RegisterParams {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}

export const authService = {
  // Đăng ký người dùng thường
  async register(params: RegisterParams) {
    const res = await apiClient.post('/api/auth/register', params);
    return res.data;
  },

  // Đăng nhập người dùng thường
  async login(params: LoginParams) {
    const res = await apiClient.post('/api/auth/login', params);
    return res.data;
  },

  // Đăng nhập trang Admin
  async adminLogin(params: LoginParams) {
    const res = await apiClient.post('/api/auth/admin/login', params);
    return res.data;
  },

  // Lấy thông tin user đang đăng nhập
  async getMe(): Promise<UserMe> {
    const res = await apiClient.get<UserMe>('/api/auth/me');
    return res.data;
  },

  // Đăng xuất
  async logout(): Promise<void> {
    await apiClient.post('/api/auth/logout', {});
  },
};
```

---

### 5.3. Auth Context / State Management (`AuthContext.tsx`)

```tsx
import React, { createContext, useContext, useEffect, useState } from 'react';
import { authService, UserMe } from './authService';

interface AuthContextType {
  user: UserMe | null;
  role: 'USER' | 'ADMIN' | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (params: any) => Promise<void>;
  adminLogin: (params: any) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<UserMe | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  const fetchCurrentUser = async () => {
    try {
      const data = await authService.getMe();
      setUser(data);
    } catch {
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCurrentUser();
  }, []);

  const login = async (params: any) => {
    await authService.login(params);
    await fetchCurrentUser();
  };

  const adminLogin = async (params: any) => {
    await authService.adminLogin(params);
    await fetchCurrentUser();
  };

  const logout = async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        role: user?.role ?? null,
        isAuthenticated: !!user,
        isLoading,
        login,
        adminLogin,
        logout,
        refreshUser: fetchCurrentUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
```

---

### 5.4. Route Guards bảo vệ trang (React Router v6)

#### User Route Guard (`ProtectedRoute.tsx`):
```tsx
import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

export const ProtectedRoute: React.FC = () => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <div>Đang tải...</div>;
  if (!isAuthenticated) return <Navigate to="/login" replace />;

  return <Outlet />;
};
```

#### Admin Route Guard (`AdminRoute.tsx`):
```tsx
import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

export const AdminRoute: React.FC = () => {
  const { isAuthenticated, role, isLoading } = useAuth();

  if (isLoading) return <div>Đang kiểm tra quyền truy cập...</div>;
  if (!isAuthenticated) return <Navigate to="/admin/login" replace />;
  if (role !== 'ADMIN') return <Navigate to="/403" replace />;

  return <Outlet />;
};
```

---

## 6. Bảng kiểm tra tích hợp Frontend (Integration Checklist)

- [ ] Đã thêm `withCredentials: true` (hoặc `credentials: 'include'`) cho tất cả các HTTP call.
- [ ] Không lưu token vào `localStorage`, `sessionStorage` hay `document.cookie`.
- [ ] Form đăng ký không cho phép người dùng chọn hoặc gửi field `role`.
- [ ] Khi khởi động app, gọi `GET /api/auth/me` để lấy thông tin phiên làm việc.
- [ ] Màn hình đăng nhập trang Admin trỏ đến `POST /api/auth/admin/login`.
- [ ] Khi nhận lỗi `401` ở request thông thường, Interceptor tự động gọi `POST /api/auth/refresh` và retry request ban đầu.
- [ ] Khi đăng xuất, gọi `POST /api/auth/logout` và xóa sạch state user trên client.
- [ ] Thông báo lỗi đăng nhập hiển thị thông điệp chung ("Sai email hoặc mật khẩu"), không phân biệt trạng thái tài khoản bị khóa hay chưa tồn tại.

---

## 7. Hướng dẫn Tích hợp Dynamic Roles (MODERATOR, SUPPORT) & Permissions

Hệ thống hỗ trợ phân quyền động linh hoạt thông qua các vai trò tùy chỉnh và danh mục quyền hạn chi tiết.

### 7.1. Danh sách API Quản lý RBAC cho Admin

#### 1. Quản lý Vai trò (Roles):
- `GET /api/auth/admin/roles`: Lấy danh sách tất cả các roles cùng danh mục quyền hạn và số lượng user gán.
- `POST /api/auth/admin/roles`: Tạo vai trò động mới (ví dụ: `MODERATOR`, `SUPPORT`, `CONTENT_REVIEWER`).
  ```json
  {
    "name": "MODERATOR",
    "description": "Điều hành viên kiểm duyệt nội dung và xử lý báo cáo",
    "permissionIds": ["uuid-1", "uuid-2"]
  }
  ```
- `GET /api/auth/admin/roles/{roleId}`: Xem chi tiết vai trò.
- `PUT /api/auth/admin/roles/{roleId}`: Sửa tên/mô tả vai trò (không thể đổi tên các system roles như `USER`, `ADMIN`).
- `DELETE /api/auth/admin/roles/{roleId}`: Xóa vai trò động (chặn xóa nếu là system role hoặc đang có user được gán).

#### 2. Danh mục Quyền hạn (Permissions):
- `GET /api/auth/admin/permissions`: Lấy danh sách toàn bộ quyền hạn theo nhóm (`category`).
- `PUT /api/auth/admin/roles/{roleId}/permissions`: Cập nhật tập quyền hạn cho một vai trò:
  ```json
  {
    "permissionIds": ["uuid-1", "uuid-2", "uuid-3"]
  }
  ```

#### 3. Quản lý Phân quyền Người dùng:
- `GET /api/auth/admin/users/{userId}/roles`: Lấy danh sách vai trò và quyền hiệu dụng (effective permissions) của người dùng:
  ```json
  {
    "userId": "uuid",
    "primaryRole": "USER",
    "roles": ["USER", "MODERATOR"],
    "effectivePermissions": [
      "CONTENT:MODERATE",
      "POST:DELETE",
      "COMMENT:DELETE",
      "REPORT:VIEW",
      "REPORT:RESOLVE"
    ]
  }
  ```
- `POST /api/auth/admin/users/{userId}/roles`: Gán vai trò cho người dùng:
  ```json
  {
    "role": "MODERATOR"
  }
  ```
- `DELETE /api/auth/admin/users/{userId}/roles/{roleName}`: Gỡ vai trò khỏi người dùng.
- `PUT /api/auth/admin/users/{userId}/roles`: Đồng bộ (ghi đè) toàn bộ danh sách vai trò của người dùng:
  ```json
  {
    "roles": ["USER", "SUPPORT"]
  }
  ```

---

### 7.2. Code mẫu Kiểm tra Quyền ở Frontend (React Hook & Component `<Can />`)

#### Hook `usePermission` & `useRole`:
```tsx
import { useAuth } from './AuthContext';

export function usePermission(permission: string): boolean {
  const { user } = useAuth();
  if (!user) return false;
  // ADMIN luôn có toàn quyền
  if (user.role === 'ADMIN') return true;
  return user.effectivePermissions?.includes(permission) ?? false;
}

export function useRole(...roles: string[]): boolean {
  const { user } = useAuth();
  if (!user) return false;
  const userRoles = user.roles || [user.role];
  return roles.some((r) => userRoles.includes(r));
}
```

#### Component kiểm soát hiển thị giao diện `<Can />`:
```tsx
import React from 'react';
import { usePermission, useRole } from './usePermission';

interface CanProps {
  permission?: string;
  role?: string;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export const Can: React.FC<CanProps> = ({ permission, role, children, fallback = null }) => {
  const hasPerm = permission ? usePermission(permission) : true;
  const hasR = role ? useRole(role) : true;

  if (hasPerm && hasR) {
    return <>{children}</>;
  }

  return <>{fallback}</>;
};

// Ví dụ sử dụng trên giao diện:
// <Can permission="POST:DELETE">
//   <button onClick={() => deletePost(postId)}>Xóa bài viết</button>
// </Can>
//
// <Can role="MODERATOR">
//   <Badge>Khu vực Điều hành viên</Badge>
// </Can>
```

