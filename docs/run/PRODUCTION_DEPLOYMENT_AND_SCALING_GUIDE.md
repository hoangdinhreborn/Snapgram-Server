# Hướng Dẫn Triển Khai Production & Cân Bằng Tải (Scaling) Snapgram

Tài liệu này hướng dẫn cách đóng toàn bộ các cổng nội bộ (chỉ mở duy nhất cổng 8080 của API Gateway), cấu hình mạng an toàn (Zero-Trust Network) và các câu lệnh vận hành, mở rộng (scale) hệ thống trên môi trường Production.

---

## 1. Nguyên Tắc An Toàn Trên Production (Zero-Trust)

### Khác biệt giữa Local Dev và Production:
| Tiêu chí | Local Development (`docker-compose.yml`) | Production (`docker-compose.prod.yml`) |
|---|---|---|
| **API Gateway** | Mở port `8080` | **Mở duy nhất port `8080` (hoặc 443/80 sau Reverse Proxy/Nginx)** |
| **Các Service con** | Mở các port `8081, 8082, 8083, 8084, 8085...` để dev gọi trực tiếp | **ĐÓNG TOÀN BỘ PORT NGOÀI**, chỉ giao tiếp qua mạng nội bộ Docker |
| **Database & Cache** | Mở port `5432-5438`, `6379`, `9092` để xem qua DBeaver | **ĐÓNG TOÀN BỘ PORT NGOÀI**, tránh bị quét brute-force qua Internet |
| **Khả năng Scale** | Bị lỗi xung đột port nếu scale > 1 container | **Scale thoải mái (2, 3, 5, 10 containers)** vì không bị kẹt port host |

---

## 2. Kiến Trúc Mạng Trên Production

```
                                  INTERNET
                                     │
                        (Chỉ mở port 8080 / 443)
                                     │
                                     ▼
                   ┌───────────────────────────────────┐
                   │     snapgram-api-gateway          │
                   │          (Port 8080)              │
                   └─────────────────┬─────────────────┘
                                     │
         ════════════════ MẠNG NỘI BỘ DOCKER (ĐÓNG PORT HOST) ════════════════
                 │                    │                     │
                 ▼                    ▼                     ▼
        ┌─────────────────┐  ┌─────────────────┐   ┌─────────────────┐
        │  auth-service   │  │ content-service │   │  media-service  │
        │  (lb://auth)    │  │  (Scale x3)     │   │  (Scale x2)     │
        └────────┬────────┘  └────────┬────────┘   └────────┬────────┘
                 │                    │                     │
                 ▼                    ▼                     ▼
           [Postgres-Auth]   [Postgres-Content]          [MinIO]
```

---

## 3. Cách Cấu Hình Đóng Port Cho Production

Để không phải sửa đè làm hỏng file `docker-compose.yml` đang dùng khi dev, ta tạo file `infra/docker-compose.prod.yml`.

Khi chạy trên Production, chỉ cần bỏ block `ports:` ở các service con và database, chuyển sang dùng `expose:` (chỉ mở nội bộ cho các container nói chuyện với nhau):

```yaml
# Ví dụ cấu hình Production cho content-service:
  content-service:
    build:
      context: ..
      dockerfile: content-service/Dockerfile
    # BỎ HOÀN TOÀN: ports: - "8084:8084"
    expose:
      - "8084"
    environment:
      - SERVER_PORT=8084
```

---

## 4. Tổng Hợp Các Câu Lệnh Vận Hành Trên Production

Tất cả câu lệnh đều thực hiện trong thư mục `infra/`:
```bash
cd infra
```

### 4.1. Khởi động hệ thống Production
```bash
# Khởi động ở chế độ background (-d)
docker compose -f docker-compose.prod.yml up -d
```

### 4.2. Mở rộng (Scale) Service để Cân Bằng Tải
Do đã tắt mapping port host, bạn có thể scale bất kỳ service nào chịu tải cao:
```bash
# Scale content-service lên 3 instances, auth-service lên 2 instances
docker compose -f docker-compose.prod.yml up -d --scale content-service=3 --scale auth-service=2
```

### 4.3. Kiểm tra trạng thái các container sau khi Scale
```bash
docker compose -f docker-compose.prod.yml ps
```
*Kết quả sẽ hiển thị:*
```
NAME                          IMAGE                             STATUS
snapgram-api-gateway          snapgram/api-gateway:latest       Up (healthy)
snapgram-auth-service-1       snapgram/auth-service:latest      Up (healthy)
snapgram-auth-service-2       snapgram/auth-service:latest      Up (healthy)
snapgram-content-service-1    snapgram/content-service:latest   Up (healthy)
snapgram-content-service-2    snapgram/content-service:latest   Up (healthy)
snapgram-content-service-3    snapgram/content-service:latest   Up (healthy)
```

### 4.4. Xem Log luân phiên tải (Verifying Load Balancing)
Xem log của toàn bộ các replicas đang chạy để kiểm tra request có được chia đều không:
```bash
# Xem log luân phiên của content-service
docker compose -f docker-compose.prod.yml logs -f --tail=30 content-service

# Xem log của Gateway
docker compose -f docker-compose.prod.yml logs -f --tail=30 api-gateway
```

### 4.5. Cập nhật code không gián đoạn (Zero-Downtime Rolling Update)
Khi có phiên bản code mới của `content-service`:
```bash
# 1. Build image mới
docker compose -f docker-compose.prod.yml build content-service

# 2. Re-create từng container mà không làm sập toàn bộ
docker compose -f docker-compose.prod.yml up -d --no-deps --scale content-service=3 content-service
```

### 4.6. Dừng hoặc Khởi động lại hệ thống
```bash
# Khởi động lại 1 service cụ thể
docker compose -f docker-compose.prod.yml restart content-service

# Tắt toàn bộ hệ thống (dữ liệu DB trong volumes vẫn giữ nguyên)
docker compose -f docker-compose.prod.yml down
```

---

## 5. Khi Lên Prod Đóng Hết Port Con Thì Test / Xem Database / Swagger Bằng Cách Nào?

Khi các port `8081, 8084, 5432...` bị đóng, bạn có 3 cách an toàn để quản trị:

### Cách 1: Sử dụng SSH Tunnel (Khuyến nghị số 1 cho Sysadmin/DevOps)
Bạn có thể kết nối DBeaver vào Database hoặc mở Swagger trên máy tính của mình thông qua SSH an toàn mà không cần mở port ra ngoài Internet:
```bash
# Tạo đường hầm kết nối thẳng vào database auth (port 5432 của server)
ssh -L 5432:localhost:5432 user@your-server-ip

# Hoặc tạo đường hầm vào Swagger UI của content-service (port 8084)
ssh -L 8084:localhost:8084 user@your-server-ip
```
Sau đó mở trình duyệt máy tính gõ `http://localhost:8084/swagger-ui.html` là truy cập được ngay.

### Cách 2: Test mọi API qua API Gateway Cổng 8080
Tất cả các API nghiệp vụ thông thường lẫn API Admin đều được gọi qua:
```http
http://your-domain.com:8080/api/...
```
- API Auth: `/api/auth/...`
- API Content: `/api/posts/...`, `/api/comments/...`
- API Admin: `/api/auth/admin/...`, `/api/admin/reports/...`

### Cách 3: Tắt Swagger trên Production để Tăng Bảo Mật
Trên môi trường Production thật sự, khuyến nghị tắt hẳn Swagger UI bằng biến môi trường trong file `.env`:
```properties
# Tắt Swagger UI để tránh lộ cấu trúc API cho kẻ tấn công
springdoc.swagger-ui.enabled=false
springdoc.api-docs.enabled=false
```
Chỉ bật Swagger trên môi trường **Staging / Testing**.
