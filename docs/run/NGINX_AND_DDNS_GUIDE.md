# HÆ°á»›ng Dáº«n TÃ­ch Há»£p Nginx Reverse Proxy, DDNS & SSL Let's Encrypt (Thá»±c Chiáº¿n Lab)

TÃ i liá»‡u hÆ°á»›ng dáº«n há»c táº­p vÃ  váº­n hÃ nh thá»±c táº¿ há»‡ thá»‘ng Edge Ingress cho Snapgram vá»›i tÃªn miá»n Ä‘á»™ng **`hqdev.ddns.net`** (No-IP DDNS).

---

## 1. Táº¡i Sao Dá»± Ãn Cáº§n Nginx Reverse Proxy?

Khi cháº¡y há»‡ thá»‘ng Microservices trong mÃ´i trÆ°á»ng thá»±c táº¿, viá»‡c má»Ÿ hÃ ng loáº¡t port ra ngoÃ i (`3000`, `3001`, `8080`, `8083`, `9000`) dáº«n Ä‘áº¿n nhiá»u rá»§i ro vÃ  báº¥t tiá»‡n:
1. **Lá»— há»•ng báº£o máº­t bá» máº·t (Attack Surface)**: Má»—i port má»Ÿ trá»±c tiáº¿p ra Internet lÃ  má»™t Ä‘iá»ƒm cÃ³ thá»ƒ bá»‹ hacker quÃ©t lá»— há»•ng vÃ  brute-force.
2. **KhÃ³ cáº¥u hÃ¬nh chá»©ng chá»‰ SSL/TLS**: Pháº£i cÃ i Ä‘áº·t chá»©ng chá»‰ SSL riÃªng láº» cho tá»«ng dá»‹ch vá»¥ vÃ  port khÃ¡c nhau.
3. **Váº¥n Ä‘á» CORS & Cookie Cross-Origin**: TrÃ¬nh duyá»‡t Ã¡p dá»¥ng chÃ­nh sÃ¡ch Same-Origin kháº¯t khe. Khi Web á»Ÿ port `3000` gá»i API á»Ÿ port `8080`, cookie xÃ¡c thá»±c (`HttpOnly`) ráº¥t dá»… bá»‹ trÃ¬nh duyá»‡t cháº·n (Third-party cookie restriction).
4. **Giá»›i háº¡n IP máº¡ng gia Ä‘Ã¬nh**: NhÃ  máº¡ng chá»‰ cáº¥p 1 Ä‘á»‹a chá»‰ IP Public. Báº¡n khÃ´ng thá»ƒ má»Ÿ 5 trang web khÃ¡c nhau cÃ¹ng cháº¡y port `80` náº¿u khÃ´ng cÃ³ Reverse Proxy Ä‘á»©ng Ä‘áº§u Ä‘á»ƒ phÃ¢n giáº£i Ä‘Æ°á»ng dáº«n.

### Vai trÃ² cá»§a Nginx trong kiáº¿n trÃºc Snapgram:
- **Single Entry Point (Cá»•ng duy nháº¥t)**: Chá»‰ má»Ÿ port `80` (HTTP) vÃ  `443` (HTTPS) duy nháº¥t ra tháº¿ giá»›i bÃªn ngoÃ i.
- **SSL/TLS Termination**: Nginx giáº£i mÃ£ SSL táº­p trung táº¡i cá»•ng vÃ o, sau Ä‘Ã³ forward gÃ³i tin HTTP ná»™i bá»™ tá»‘c Ä‘á»™ cao vÃ o Docker network.
- **URL Path-Based Routing**: Dá»±a vÃ o tiá»n tá»‘ URL (`/api/`, `/storage/`, `/ws/`, `/admin/`, `/`) Ä‘á»ƒ Ä‘iá»u hÆ°á»›ng chÃ­nh xÃ¡c vÃ o microservice tÆ°Æ¡ng á»©ng.
- **Äá»™ trá»… tháº¥p & Tá»‘i Æ°u bÄƒng thÃ´ng**: Tá»± Ä‘á»™ng nÃ©n Gzip cÃ¡c file tÄ©nh, há»— trá»£ HTTP/2 Ä‘a luá»“ng (Multiplexing).
- **Há»— trá»£ WebSockets**: NÃ¢ng cáº¥p káº¿t ná»‘i HTTP sang TCP Stream liÃªn tá»¥c phá»¥c vá»¥ tÃ­nh nÄƒng Nháº¯n tin vÃ  ThÃ´ng bÃ¡o real-time.

---

## 2. Báº£n Äá»“ Äá»‹nh Tuyáº¿n (Routing Table)

ToÃ n bá»™ dá»‹ch vá»¥ trong há»‡ thá»‘ng Ä‘Æ°á»£c gom chung vá» má»™t Ä‘á»‹a chá»‰ duy nháº¥t: **`https://hqdev.ddns.net`**:

```
[Internet / TrÃ¬nh duyá»‡t]
           â”‚ HTTPS (:443) / HTTP (:80 -> 301)
           â–¼
   â”Œâ”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”
   â”‚                     SNAPGRAM NGINX EDGE                     â”‚
   â””â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”¬â”€â”€â”€â”€â”€â”€â”€â”€â”€â”˜
           â”‚              â”‚              â”‚             â”‚
    location /api   location /ws   location /storage  location /
           â”‚              â”‚              â”‚             â”‚
           â–¼              â–¼              â–¼             â–¼
      API Gateway    WebSockets      MinIO S3     Next.js Web
      (:8080)        (:8080/ws)      (:9000)      (:3000)
```

| URL trÃªn trÃ¬nh duyá»‡t | Upstream Ä‘Ã­ch ná»™i bá»™ | MÃ´ táº£ nghiá»‡p vá»¥ |
|---|---|---|
| `https://hqdev.ddns.net/` | `host.docker.internal:3000` | Giao diá»‡n máº¡ng xÃ£ há»™i Web Client (Next.js) |
| `https://hqdev.ddns.net/admin` | `host.docker.internal:3001` | Giao diá»‡n quáº£n trá»‹ Admin Portal (Next.js) |
| `https://hqdev.ddns.net/api/**` | `api-gateway:8080` | ToÃ n bá»™ API Backend (Auth, Content, Media, Payment, Batch...) |
| `https://hqdev.ddns.net/ws/**` | `api-gateway:8080` | KÃªnh káº¿t ná»‘i WebSocket Chat & Notifications (Upgrade: websocket) |
| `https://hqdev.ddns.net/storage/**` | `minio:9000` | Truy xuáº¥t áº£nh/video/avatar trá»±c tiáº¿p tá»« bucket S3 |
| `https://hqdev.ddns.net/health` | `api-gateway:8080/actuator/health` | Kiá»ƒm tra tráº¡ng thÃ¡i hoáº¡t Ä‘á»™ng cá»§a há»‡ thá»‘ng (`200 UP`) |
| `http://hqdev.ddns.net/.well-known/acme-challenge/*` | `/var/www/certbot` | XÃ¡c thá»±c cáº¥p chá»©ng chá»‰ SSL tá»± Ä‘á»™ng cá»§a Let's Encrypt |

---

## 3. Cáº¥u TrÃºc File & ThÆ° Má»¥c Cáº¥u HÃ¬nh Nginx

Cáº¥u hÃ¬nh Nginx Ä‘Æ°á»£c module hÃ³a chuyÃªn nghiá»‡p trong thÆ° má»¥c `Snapgram_server/infra/nginx/`:

```
Snapgram_server/infra/nginx/
â”œâ”€â”€ nginx.conf                 # Cáº¥u hÃ¬nh lÃµi Nginx (worker_processes, gzip, log format, timeouts)
â”œâ”€â”€ proxy_params               # Bá»™ header chuáº©n Ä‘Ã­nh kÃ¨m (Host, X-Real-IP, X-Forwarded-*, buffer)
â”œâ”€â”€ ssl_params                 # Chuáº©n mÃ£ hÃ³a TLS 1.2/1.3, ciphers báº£o máº­t vÃ  security headers
â”œâ”€â”€ conf.d/
â”‚   â”œâ”€â”€ websocket.conf         # Mapping chuyá»ƒn Ä‘á»•i káº¿t ná»‘i HTTP Upgrade sang WebSocket
â”‚   â””â”€â”€ default.conf           # Server blocks: Cáº¥u hÃ¬nh Port 80 (Redirect & ACME) vÃ  Port 443 (Reverse Proxy)
â””â”€â”€ certbot/
    â”œâ”€â”€ conf/                  # NÆ¡i lÆ°u trá»¯ chá»©ng chá»‰ SSL Let's Encrypt (fullchain.pem, privkey.pem)
    â””â”€â”€ www/                   # ThÆ° má»¥c webroot chia sáº» vá»›i Certbot phá»¥c vá»¥ challenge HTTP-01
```

---

## 4. HÆ°á»›ng Dáº«n Cáº¥u HÃ¬nh Máº¡ng Thá»±c Táº¿ (DDNS & Router)

Äá»ƒ mÃ¡y tÃ­nh bÃªn ngoÃ i Internet cÃ³ thá»ƒ truy cáº­p vÃ o mÃ¡y tÃ­nh cá»§a báº¡n qua tÃªn miá»n `hqdev.ddns.net`, báº¡n cáº§n thá»±c hiá»‡n 3 bÆ°á»›c cáº¥u hÃ¬nh máº¡ng:

### 4.1 CÃ i Ä‘áº·t No-IP Dynamic Update Client (DUC)
IP Public cá»§a máº¡ng gia Ä‘Ã¬nh (Viettel/VNPT/FPT) sáº½ thay Ä‘á»•i má»—i khi router khá»Ÿi Ä‘á»™ng láº¡i. DUC sáº½ tá»± Ä‘á»™ng theo dÃµi IP cá»§a báº¡n vÃ  cáº­p nháº­t vÃ o domain `hqdev.ddns.net`.
1. ÄÄƒng kÃ½ tÃ i khoáº£n táº¡i [noip.com](https://www.noip.com/) vÃ  táº¡o hostname `hqdev.ddns.net`.
2. Táº£i pháº§n má»m **No-IP DUC** cho Windows hoáº·c Linux:
   - Táº£i tá»«: [https://www.noip.com/download](https://www.noip.com/download)
3. ÄÄƒng nháº­p DUC vÃ  chá»n tÃ­ch vÃ o domain `hqdev.ddns.net` Ä‘á»ƒ client tá»± Ä‘á»™ng Ä‘á»“ng bá»™ IP.

### 4.2 Kiá»ƒm tra hiá»‡n tÆ°á»£ng CGNAT (Ráº¥t quan trá»ng táº¡i Viá»‡t Nam)
Nhiá»u nhÃ  máº¡ng Viá»‡t Nam máº·c Ä‘á»‹nh gom khÃ¡ch hÃ ng cÃ¡ nhÃ¢n vÃ o dáº£i máº¡ng ná»™i bá»™ **CGNAT** (dáº£i `100.64.0.0/10` hoáº·c `10.x.x.x`), khiáº¿n báº¡n khÃ´ng thá»ƒ má»Ÿ port trá»±c tiáº¿p.
- **CÃ¡ch kiá»ƒm tra:**
  1. ÄÄƒng nháº­p vÃ o trang quáº£n trá»‹ modem router (thÆ°á»ng lÃ  `192.168.1.1`).
  2. Xem má»¥c **WAN IP / IPv4 Address** trong pháº§n Status.
  3. Má»Ÿ trang web [https://www.whatismyip.com](https://www.whatismyip.com).
  - *Náº¿u 2 IP nÃ y trÃ¹ng nhau:* Báº¡n Ä‘ang cÃ³ **IP Public tháº­t**, hoÃ n toÃ n má»Ÿ port Ä‘Æ°á»£c!
  - *Náº¿u 2 IP nÃ y khÃ¡c nhau:* Báº¡n Ä‘ang bá»‹ dÃ­nh CGNAT.
- **CÃ¡ch giáº£i quyáº¿t CGNAT:**
  Gá»i Ä‘iá»‡n lÃªn tá»•ng Ä‘Ã i nhÃ  máº¡ng (Viettel: `18008119`, FPT: `19006600`, VNPT: `18001166`) vÃ  nÃ³i: *"NhÃ  em Ä‘ang láº¯p camera an ninh vÃ  há»c láº­p trÃ¬nh, nhá» ká»¹ thuáº­t má»Ÿ giÃºp em IP WAN Public Ä‘á»™ng (NAT 1-1) Ä‘á»ƒ em má»Ÿ port"*. NhÃ¢n viÃªn nhÃ  máº¡ng sáº½ má»Ÿ cho báº¡n hoÃ n toÃ n miá»…n phÃ­ trong vÃ²ng 5-10 phÃºt.

### 4.3 Cáº¥u hÃ¬nh Port Forwarding trÃªn Router Modem
Chuyá»ƒn hÆ°á»›ng lÆ°u lÆ°á»£ng truy cáº­p tá»« ngoÃ i Internet vÃ o Ä‘Ãºng mÃ¡y tÃ­nh cá»§a báº¡n trong máº¡ng LAN:
1. Äáº·t IP tÄ©nh (Static IP LAN) cho mÃ¡y tÃ­nh cháº¡y dá»± Ã¡n (vÃ­ dá»¥: `192.168.1.50`).
2. Truy cáº­p router modem (`192.168.1.1`), tÃ¬m má»¥c **Port Forwarding** hoáº·c **Virtual Server**.
3. ThÃªm 2 báº£n ghi:
   - **Báº£n ghi 1 (HTTP):**
     - External Port: `80`
     - Internal Port: `80`
     - Protocol: `TCP`
     - Internal IP: `192.168.1.50` (IP mÃ¡y tÃ­nh cá»§a báº¡n)
   - **Báº£n ghi 2 (HTTPS):**
     - External Port: `443`
     - Internal Port: `443`
     - Protocol: `TCP`
     - Internal IP: `192.168.1.50` (IP mÃ¡y tÃ­nh cá»§a báº¡n)

---

## 5. Hiá»‡n TÆ°á»£ng NAT Loopback & Cáº¥u HÃ¬nh File `hosts` Local

### 5.1 NAT Loopback (Hairpinning) lÃ  gÃ¬?
Khi báº¡n ngá»“i trong máº¡ng Wi-Fi á»Ÿ nhÃ  vÃ  gÃµ `https://hqdev.ddns.net`:
- TrÃ¬nh duyá»‡t phÃ¢n giáº£i `hqdev.ddns.net` ra IP Public cá»§a chÃ­nh modem router nhÃ  báº¡n.
- Nhiá»u dÃ²ng modem giÃ¡ ráº» khÃ´ng há»— trá»£ **NAT Loopback**, router sáº½ tá»« chá»‘i káº¿t ná»‘i hoáº·c hiá»ƒn thá»‹ trang Ä‘Äƒng nháº­p admin cá»§a modem thay vÃ¬ chuyá»ƒn tiáº¿p vÃ o mÃ¡y tÃ­nh cháº¡y Docker.

### 5.2 CÃ¡ch xá»­ lÃ½ khi há»c vÃ  dev trÃªn mÃ¡y ná»™i bá»™:
Chá»‰ cáº§n cáº¥u hÃ¬nh file `hosts` trÃªn mÃ¡y tÃ­nh Ä‘á»ƒ khi gÃµ `hqdev.ddns.net`, mÃ¡y tÃ­nh sáº½ tá»± Ä‘á»™ng trá» tháº³ng vÃ o Nginx cá»¥c bá»™ (`127.0.0.1`):
1. Má»Ÿ **Notepad** báº±ng quyá»n Administrator (**Run as administrator**).
2. Má»Ÿ file: `C:\Windows\System32\drivers\etc\hosts`.
3. ThÃªm dÃ²ng sau vÃ o cuá»‘i file vÃ  lÆ°u láº¡i:
   ```text
   127.0.0.1 hqdev.ddns.net
   ```
4. Má»Ÿ PowerShell vÃ  xÃ³a cache DNS:
   ```powershell
   ipconfig /flushdns
   ```
BÃ¢y giá», má»i request Ä‘áº¿n `http://hqdev.ddns.net` hoáº·c `https://hqdev.ddns.net` tá»« mÃ¡y báº¡n sáº½ Ä‘i trá»±c tiáº¿p qua Nginx Reverse Proxy mÃ  khÃ´ng cáº§n lo láº¯ng vá» router!

---

## 6. Váº­n HÃ nh SSL Let's Encrypt

Há»‡ thá»‘ng Ä‘Ã£ Ä‘Æ°á»£c trang bá»‹ sáºµn **Bootstrap Certificate** (chá»©ng chá»‰ tá»± kÃ½ táº¡m thá»i) Ä‘á»ƒ Nginx cÃ³ thá»ƒ khá»Ÿi Ä‘á»™ng ngay láº­p tá»©c mÃ  khÃ´ng bá»‹ lá»—i thiáº¿u file.

### 6.1 Láº¥y Chá»©ng Chá»‰ SSL Tháº­t tá»« Let's Encrypt
Khi báº¡n Ä‘Ã£ hoÃ n thÃ nh Port Forwarding trÃªn router vÃ  domain `hqdev.ddns.net` Ä‘Ã£ trá» Ä‘Ãºng vá» IP nhÃ  báº¡n:

#### CÃ¡ch 1: Cháº¡y báº±ng PowerShell trÃªn Windows:
```powershell
cd D:\Source\Snapgram\Snapgram_server\infra
.\init-letsencrypt.ps1 -Email your-email@gmail.com
```

#### CÃ¡ch 2: Cháº¡y báº±ng Bash (Linux / WSL / Git Bash):
```bash
cd D:/Source/Snapgram/Snapgram_server/infra
chmod +x init-letsencrypt.sh
./init-letsencrypt.sh your-email@gmail.com
```

*LÆ°u Ã½:* Náº¿u Ä‘ang trong quÃ¡ trÃ¬nh thá»­ nghiá»‡m Ä‘Æ°á»ng truyá»n máº¡ng, báº¡n cÃ³ thá»ƒ truyá»n thÃªm tham sá»‘ Staging Ä‘á»ƒ trÃ¡nh bá»‹ Let's Encrypt khÃ³a IP táº¡m thá»i (rate limit 5 láº§n thá»­ tháº¥t báº¡i/giá»):
```powershell
.\init-letsencrypt.ps1 -Email your-email@gmail.com -Staging
```

### 6.2 CÆ¡ Cháº¿ Tá»± Äá»™ng Gia Háº¡n (Auto-Renewal)
Chá»©ng chá»‰ Let's Encrypt cÃ³ thá»i háº¡n **90 ngÃ y**. Container `snapgram-certbot` trong `docker-compose.yml` Ä‘Æ°á»£c cáº¥u hÃ¬nh tá»± Ä‘á»™ng cháº¡y lá»‡nh kiá»ƒm tra gia háº¡n má»—i 12 giá»:
```sh
certbot renew --webroot -w /var/www/certbot --quiet
```
Khi chá»©ng chá»‰ cÃ²n dÆ°á»›i 30 ngÃ y háº¿t háº¡n, Certbot sáº½ tá»± Ä‘á»™ng gia háº¡n thÃ nh cÃ´ng mÃ  khÃ´ng lÃ m giÃ¡n Ä‘oáº¡n há»‡ thá»‘ng.

---

## 7. CÃ¡c Lá»‡nh Quáº£n Trá»‹ Nginx Nhanh (Cheatsheet)

| Thao tÃ¡c | CÃ¢u lá»‡nh thá»±c thi |
|---|---|
| **Kiá»ƒm tra cÃº phÃ¡p cáº¥u hÃ¬nh** | `docker exec snapgram-nginx nginx -t` |
| **Náº¡p láº¡i cáº¥u hÃ¬nh (khÃ´ng downtime)** | `docker exec snapgram-nginx nginx -s reload` |
| **Xem log truy cáº­p theo thá»i gian thá»±c** | `docker logs -f snapgram-nginx` |
| **Kiá»ƒm tra tráº¡ng thÃ¡i sá»©c khá»e** | `docker exec snapgram-nginx wget -q -O- http://127.0.0.1:80/health` |
| **Khá»Ÿi Ä‘á»™ng cá»¥m dá»‹ch vá»¥ máº¡ng** | `docker compose -f infra/docker-compose.yml up -d nginx certbot` |
| **Khá»Ÿi Ä‘á»™ng láº¡i toÃ n bá»™ cá»¥m** | `docker compose -f infra/docker-compose.yml restart nginx` |

