# Hướng Dẫn Tích Hợp Nginx Reverse Proxy, DDNS & SSL Let's Encrypt (Thực Chiến Lab)

Tài liệu hướng dẫn học tập và vận hành thực tế hệ thống Edge Ingress cho Snapgram với tên miền động **`maintaindev.id.vn`** (No-IP DDNS).

---

## 1. Tại Sao Dự Án Cần Nginx Reverse Proxy?

Khi chạy hệ thống Microservices trong môi trường thực tế, việc mở hàng loạt port ra ngoài (`3000`, `3001`, `8080`, `8083`, `9000`) dẫn đến nhiều rủi ro và bất tiện:
1. **Lỗ hổng bảo mật bề mặt (Attack Surface)**: Mỗi port mở trực tiếp ra Internet là một điểm có thể bị hacker quét lỗ hổng và brute-force.
2. **Khó cấu hình chứng chỉ SSL/TLS**: Phải cài đặt chứng chỉ SSL riêng lẻ cho từng dịch vụ và port khác nhau.
3. **Vấn đề CORS & Cookie Cross-Origin**: Trình duyệt áp dụng chính sách Same-Origin khắt khe. Khi Web ở port `3000` gọi API ở port `8080`, cookie xác thực (`HttpOnly`) rất dễ bị trình duyệt chặn (Third-party cookie restriction).
4. **Giới hạn IP mạng gia đình**: Nhà mạng chỉ cấp 1 địa chỉ IP Public. Bạn không thể mở 5 trang web khác nhau cùng chạy port `80` nếu không có Reverse Proxy đứng đầu để phân giải đường dẫn.

### Vai trò của Nginx trong kiến trúc Snapgram:
- **Single Entry Point (Cổng duy nhất)**: Chỉ mở port `80` (HTTP) và `443` (HTTPS) duy nhất ra thế giới bên ngoài.
- **SSL/TLS Termination**: Nginx giải mã SSL tập trung tại cổng vào, sau đó forward gói tin HTTP nội bộ tốc độ cao vào Docker network.
- **URL Path-Based Routing**: Dựa vào tiền tố URL (`/api/`, `/storage/`, `/ws/`, `/admin/`, `/`) để điều hướng chính xác vào microservice tương ứng.
- **Độ trễ thấp & Tối ưu băng thông**: Tự động nén Gzip các file tĩnh, hỗ trợ HTTP/2 đa luồng (Multiplexing).
- **Hỗ trợ WebSockets**: Nâng cấp kết nối HTTP sang TCP Stream liên tục phục vụ tính năng Nhắn tin và Thông báo real-time.

---

## 2. Bản Đồ Định Tuyến (Routing Table)

Toàn bộ dịch vụ trong hệ thống được gom chung về một địa chỉ duy nhất: **`https://maintaindev.id.vn`**:

```
[Internet / Trình duyệt]
           │ HTTPS (:443) / HTTP (:80 -> 301)
           ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                     SNAPGRAM NGINX EDGE                     │
   └───────┬──────────────┬──────────────┬─────────────┬─────────┘
           │              │              │             │
    location /api   location /ws   location /storage  location /
           │              │              │             │
           ▼              ▼              ▼             ▼
      API Gateway    WebSockets      MinIO S3     Next.js Web
      (:8080)        (:8080/ws)      (:9000)      (:3000)
```

| URL trên trình duyệt | Upstream đích nội bộ | Mô tả nghiệp vụ |
|---|---|---|
| `https://maintaindev.id.vn/` | `host.docker.internal:3000` | Giao diện mạng xã hội Web Client (Next.js) |
| `https://maintaindev.id.vn/admin` | `host.docker.internal:3001` | Giao diện quản trị Admin Portal (Next.js) |
| `https://maintaindev.id.vn/api/**` | `api-gateway:8080` | Toàn bộ API Backend (Auth, Content, Media, Payment, Batch...) |
| `https://maintaindev.id.vn/ws/**` | `api-gateway:8080` | Kênh kết nối WebSocket Chat & Notifications (Upgrade: websocket) |
| `https://maintaindev.id.vn/storage/**` | `minio:9000` | Truy xuất ảnh/video/avatar trực tiếp từ bucket S3 |
| `https://maintaindev.id.vn/health` | `api-gateway:8080/actuator/health` | Kiểm tra trạng thái hoạt động của hệ thống (`200 UP`) |
| `http://maintaindev.id.vn/.well-known/acme-challenge/*` | `/var/www/certbot` | Xác thực cấp chứng chỉ SSL tự động của Let's Encrypt |

---

## 3. Cấu Trúc File & Thư Mục Cấu Hình Nginx

Cấu hình Nginx được module hóa chuyên nghiệp trong thư mục `Snapgram_server/infra/nginx/`:

```
Snapgram_server/infra/nginx/
├── nginx.conf                 # Cấu hình lõi Nginx (worker_processes, gzip, log format, timeouts)
├── proxy_params               # Bộ header chuẩn đính kèm (Host, X-Real-IP, X-Forwarded-*, buffer)
├── ssl_params                 # Chuẩn mã hóa TLS 1.2/1.3, ciphers bảo mật và security headers
├── conf.d/
│   ├── websocket.conf         # Mapping chuyển đổi kết nối HTTP Upgrade sang WebSocket
│   └── default.conf           # Server blocks: Cấu hình Port 80 (Redirect & ACME) và Port 443 (Reverse Proxy)
└── certbot/
    ├── conf/                  # Nơi lưu trữ chứng chỉ SSL Let's Encrypt (fullchain.pem, privkey.pem)
    └── www/                   # Thư mục webroot chia sẻ với Certbot phục vụ challenge HTTP-01
```

---

## 4. Hướng Dẫn Cấu Hình Mạng Thực Tế (DDNS & Router)

Để máy tính bên ngoài Internet có thể truy cập vào máy tính của bạn qua tên miền `maintaindev.id.vn`, bạn cần thực hiện 3 bước cấu hình mạng:

### 4.1 Cài đặt No-IP Dynamic Update Client (DUC)
IP Public của mạng gia đình (Viettel/VNPT/FPT) sẽ thay đổi mỗi khi router khởi động lại. DUC sẽ tự động theo dõi IP của bạn và cập nhật vào domain `maintaindev.id.vn`.
1. Đăng ký tài khoản tại [noip.com](https://www.noip.com/) và tạo hostname `maintaindev.id.vn`.
2. Tải phần mềm **No-IP DUC** cho Windows hoặc Linux:
   - Tải từ: [https://www.noip.com/download](https://www.noip.com/download)
3. Đăng nhập DUC và chọn tích vào domain `maintaindev.id.vn` để client tự động đồng bộ IP.

### 4.2 Kiểm tra hiện tượng CGNAT (Rất quan trọng tại Việt Nam)
Nhiều nhà mạng Việt Nam mặc định gom khách hàng cá nhân vào dải mạng nội bộ **CGNAT** (dải `100.64.0.0/10` hoặc `10.x.x.x`), khiến bạn không thể mở port trực tiếp.
- **Cách kiểm tra:**
  1. Đăng nhập vào trang quản trị modem router (thường là `192.168.1.1`).
  2. Xem mục **WAN IP / IPv4 Address** trong phần Status.
  3. Mở trang web [https://www.whatismyip.com](https://www.whatismyip.com).
  - *Nếu 2 IP này trùng nhau:* Bạn đang có **IP Public thật**, hoàn toàn mở port được!
  - *Nếu 2 IP này khác nhau:* Bạn đang bị dính CGNAT.
- **Cách giải quyết CGNAT:**
  Gọi điện lên tổng đài nhà mạng (Viettel: `18008119`, FPT: `19006600`, VNPT: `18001166`) và nói: *"Nhà em đang lắp camera an ninh và học lập trình, nhờ kỹ thuật mở giúp em IP WAN Public động (NAT 1-1) để em mở port"*. Nhân viên nhà mạng sẽ mở cho bạn hoàn toàn miễn phí trong vòng 5-10 phút.

### 4.3 Cấu hình Port Forwarding trên Router Modem
Chuyển hướng lưu lượng truy cập từ ngoài Internet vào đúng máy tính của bạn trong mạng LAN:
1. Đặt IP tĩnh (Static IP LAN) cho máy tính chạy dự án (ví dụ: `192.168.1.50`).
2. Truy cập router modem (`192.168.1.1`), tìm mục **Port Forwarding** hoặc **Virtual Server**.
3. Thêm 2 bản ghi:
   - **Bản ghi 1 (HTTP):**
     - External Port: `80`
     - Internal Port: `80`
     - Protocol: `TCP`
     - Internal IP: `192.168.1.50` (IP máy tính của bạn)
   - **Bản ghi 2 (HTTPS):**
     - External Port: `443`
     - Internal Port: `443`
     - Protocol: `TCP`
     - Internal IP: `192.168.1.50` (IP máy tính của bạn)

---

## 5. Hiện Tượng NAT Loopback & Cấu Hình File `hosts` Local

### 5.1 NAT Loopback (Hairpinning) là gì?
Khi bạn ngồi trong mạng Wi-Fi ở nhà và gõ `https://maintaindev.id.vn`:
- Trình duyệt phân giải `maintaindev.id.vn` ra IP Public của chính modem router nhà bạn.
- Nhiều dòng modem giá rẻ không hỗ trợ **NAT Loopback**, router sẽ từ chối kết nối hoặc hiển thị trang đăng nhập admin của modem thay vì chuyển tiếp vào máy tính chạy Docker.

### 5.2 Cách xử lý khi học và dev trên máy nội bộ:
Chỉ cần cấu hình file `hosts` trên máy tính để khi gõ `maintaindev.id.vn`, máy tính sẽ tự động trỏ thẳng vào Nginx cục bộ (`127.0.0.1`):
1. Mở **Notepad** bằng quyền Administrator (**Run as administrator**).
2. Mở file: `C:\Windows\System32\drivers\etc\hosts`.
3. Thêm dòng sau vào cuối file và lưu lại:
   ```text
   127.0.0.1 maintaindev.id.vn
   ```
4. Mở PowerShell và xóa cache DNS:
   ```powershell
   ipconfig /flushdns
   ```
Bây giờ, mọi request đến `http://maintaindev.id.vn` hoặc `https://maintaindev.id.vn` từ máy bạn sẽ đi trực tiếp qua Nginx Reverse Proxy mà không cần lo lắng về router!

---

## 6. Vận Hành SSL Let's Encrypt

Hệ thống đã được trang bị sẵn **Bootstrap Certificate** (chứng chỉ tự ký tạm thời) để Nginx có thể khởi động ngay lập tức mà không bị lỗi thiếu file.

### 6.1 Lấy Chứng Chỉ SSL Thật từ Let's Encrypt
Khi bạn đã hoàn thành Port Forwarding trên router và domain `maintaindev.id.vn` đã trỏ đúng về IP nhà bạn:

#### Cách 1: Chạy bằng PowerShell trên Windows:
```powershell
cd D:\Source\Snapgram\Snapgram_server\infra
.\init-letsencrypt.ps1 -Email your-email@gmail.com
```

#### Cách 2: Chạy bằng Bash (Linux / WSL / Git Bash):
```bash
cd D:/Source/Snapgram/Snapgram_server/infra
chmod +x init-letsencrypt.sh
./init-letsencrypt.sh your-email@gmail.com
```

*Lưu ý:* Nếu đang trong quá trình thử nghiệm đường truyền mạng, bạn có thể truyền thêm tham số Staging để tránh bị Let's Encrypt khóa IP tạm thời (rate limit 5 lần thử thất bại/giờ):
```powershell
.\init-letsencrypt.ps1 -Email your-email@gmail.com -Staging
```

### 6.2 Cơ Chế Tự Động Gia Hạn (Auto-Renewal)
Chứng chỉ Let's Encrypt có thời hạn **90 ngày**. Container `snapgram-certbot` trong `docker-compose.yml` được cấu hình tự động chạy lệnh kiểm tra gia hạn mỗi 12 giờ:
```sh
certbot renew --webroot -w /var/www/certbot --quiet
```
Khi chứng chỉ còn dưới 30 ngày hết hạn, Certbot sẽ tự động gia hạn thành công mà không làm gián đoạn hệ thống.

---

## 7. Các Lệnh Quản Trị Nginx Nhanh (Cheatsheet)

| Thao tác | Câu lệnh thực thi |
|---|---|
| **Kiểm tra cú pháp cấu hình** | `docker exec snapgram-nginx nginx -t` |
| **Nạp lại cấu hình (không downtime)** | `docker exec snapgram-nginx nginx -s reload` |
| **Xem log truy cập theo thời gian thực** | `docker logs -f snapgram-nginx` |
| **Kiểm tra trạng thái sức khỏe** | `docker exec snapgram-nginx wget -q -O- http://127.0.0.1:80/health` |
| **Khởi động cụm dịch vụ mạng** | `docker compose -f infra/docker-compose.yml up -d nginx certbot` |
| **Khởi động lại toàn bộ cụm** | `docker compose -f infra/docker-compose.yml restart nginx` |
