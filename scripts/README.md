# Hướng dẫn Chạy Script Seed Data Demo (Snapgram)

Tài liệu hướng dẫn khởi tạo dữ liệu mẫu phong phú và tái lập được (reproducible) cho hệ thống mạng xã hội **Snapgram**.

---

## 1. Yêu cầu Môi trường (Prerequisites)

1. **PowerShell 7** (`pwsh`) hoặc **Windows PowerShell 5.1** (hoặc `bash` + `curl` + `jq` trên Linux/macOS/Git Bash).
2. **FFmpeg** (`ffmpeg` có trong PATH): Dùng để tự động render 12 video ngắn dọc 8s (1080x1920) chuẩn H.264/AAC.
   *(Nếu máy chưa cài FFmpeg, script sẽ tự động fallback bài viết video sang ảnh và hiển thị cảnh báo)*.
3. **Docker Desktop & Hạ tầng**:
   Đảm bảo hạ tầng (Postgres, Kafka, Redis, MinIO) và các service backend đã khởi động trước khi seed:
   ```bash
   cd infra
   docker compose up -d
   ```
4. **Cấu hình Turnstile**:
   Đảm bảo file `.env` đã có cấu hình:
   ```env
   TURNSTILE_ENABLED=false
   ```
   *(Nhằm tránh Cloudflare Bot Challenge chặn script tự động khi gọi API đăng ký/đăng nhập)*.

---

## 2. Thứ tự Thực thi & Cách Chạy

### Cách 1: Sử dụng PowerShell (Khuyến nghị cho Windows)

1. **Kiểm tra kết nối & tài khoản probe (-ProbeOnly)**:
   ```powershell
   pwsh ./scripts/seed-demo.ps1 -ProbeOnly
   ```
   *(Hoặc chạy trong Windows PowerShell 5.1: `powershell -ExecutionPolicy Bypass -File ./scripts/seed-demo.ps1 -ProbeOnly`)*

2. **Chạy Seed toàn bộ dữ liệu**:
   ```powershell
   pwsh ./scripts/seed-demo.ps1
   ```

3. **Chạy lặp lại (Idempotent)**:
   - Script lưu vết trạng thái tại `scripts/seed/state.json`. Khi chạy lại lệnh trên mà không có `-Force`, script sẽ phát hiện user/post đã tạo và bỏ qua an toàn mà không nhân đôi dữ liệu.
   - Nếu muốn ép buộc seed lại từ đầu:
     ```powershell
     pwsh ./scripts/seed-demo.ps1 -Force
     ```

### Cách 2: Sử dụng Bash Script (Git Bash, WSL, Linux, macOS)

```bash
# Cấp quyền thực thi
chmod +x ./scripts/seed-demo.sh

# Chạy probe test
./scripts/seed-demo.sh --probe-only

# Chạy seed toàn bộ
./scripts/seed-demo.sh
```

---

## 3. Tài khoản Đăng nhập Demo

Sau khi chạy xong script, bạn có thể đăng nhập vào ứng dụng web/mobile bằng tài khoản demo:

- **Email**: `demo@demo.local`
- **Mật khẩu**: `Demo@12345`
- **Quyền hạn**: `USER` (hiển thị công khai trên feed/explore)
- **Huy hiệu**: Đã kích hoạt **Tick xanh (Verified Badge)** và xác thực email.
- **Tài khoản Admin Portal**: `admin@snapgram.com` (mật khẩu cấu hình trong `ADMIN_INITIAL_PASSWORD`).

*(Các tài khoản phụ từ `user1@demo.local` đến `user20@demo.local` đều có cùng mật khẩu `Demo@12345`)*.

---

## 4. Cách Reset Sạch Dữ liệu (Xóa trắng & Seed lại)

Khi muốn làm sạch toàn bộ dữ liệu để chuẩn bị cho buổi demo mới:

1. **Dừng và xóa toàn bộ dữ liệu Docker Volume**:
   ```bash
   cd infra
   docker compose down -v
   docker compose up -d
   ```
   *(Flyway sẽ tự động chạy lại migration khi các service khởi động lại)*.

2. **Xóa file trạng thái và assets cũ (nếu muốn tải mới)**:
   ```powershell
   Remove-Item -Recurse -Force ./scripts/seed/state.json
   ```

3. **Chạy lại script seed**:
   ```powershell
   pwsh ./scripts/seed-demo.ps1 -Force
   ```

> ⚠️ **Lưu ý quan trọng**: Stories và Notes trên Instagram/Snapgram có cơ chế tự động hết hạn sau **24 giờ**. Hãy chạy lại script ngay trước giờ demo để nội dung Story và Note luôn hiển thị đầy đủ và sinh động nhất!
