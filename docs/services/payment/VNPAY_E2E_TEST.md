# VNPay E2E — tự test sandbox

> Hướng dẫn chạy end-to-end thanh toán **VNPay sandbox** cho `payment-service` + `auth-service`.
> Đã chạy thực tế ngày 2026-10-20: tạo đơn, QueryDR, IPN ký hợp lệ, outbox → Kafka → cấp Blue Tick.
> Bước quẹt thẻ/OTP trên trang VNPay cần bạn tự làm bằng trình duyệt.

---

## 0. Chọn kiểu test

| Kiểu | Cần gì | IPN thật? | Thời gian |
|---|---|---|---|
| **A. Thanh toán thật** | Trình duyệt + thẻ NCB sandbox | Không bắt buộc (dùng reconcile) | ~5 phút |
| **B. IPN giả lập** | Chỉ terminal, không cần thẻ | Không (tự ký bằng secret) | ~2 phút |
| **C. IPN thật qua tunnel** | Tunnel public + IPN URL đã đăng ký trên portal VNPay | Có | ~10 phút |

Khuyến nghị: chạy **B** trước để chắc pipeline trong nước hoạt động, rồi **A** để thử trang thanh toán thật.

---

## 1. Chuẩn bị

### 1.1. Biến môi trường

Kiểm tra `.env` ở thư mục gốc có đủ:

```bash
grep -E '^VNPAY_(TMN_CODE|HASH_SECRET|RETURN_URL)=' .env
```

- `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`: bắt buộc, lấy từ merchant sandbox.
- Với test local, **override** return URL khi start payment-service (không sửa `.env`):

```bash
export VNPAY_RETURN_URL='http://localhost:8080/api/payments/vnpay/return'
```

> VNPay **không có biến môi trường IPN URL**: URL IPN gắn với Terminal/TmnCode trên merchant portal.
> Cách cấu hình xem mục 5.

### 1.2. Hạ tầng

```bash
cd infra
docker compose up -d postgres-auth postgres-payment redis kafka kafka-init
docker compose ps
```

Nếu chạy service bằng Maven trên host, Kafka trong compose advertise `kafka:9092` (chỉ resolve trong Docker).
Chọn một trong hai cách:

```bash
# Cách 1 (có sudo): thêm alias để host resolve được
sudo sh -c 'grep -q " kafka$" /etc/hosts || echo "127.0.0.1 kafka" >> /etc/hosts'
```

```bash
# Cách 2 (không sudo): chạy Kafka tạm advertise localhost:9092
# (bỏ "kafka kafka-init" khỏi lệnh compose ở trên để không chiếm port 9092)
docker rm -f snapgram-kafka-e2e 2>/dev/null
docker run -d --name snapgram-kafka-e2e -p 9092:9092 \
  -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
  apache/kafka:4.3.1
sleep 8
docker exec snapgram-kafka-e2e /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --topic payment.completed \
  --partitions 3 --replication-factor 1
```

> Nếu chạy cả 3 service bằng Docker Compose thì bỏ qua phần Kafka host ở trên.

### 1.3. Chạy 3 service (3 terminal)

```bash
# Terminal 1 — auth-service (port 8081)
set -a && . ./.env && set +a
export DB_URL='jdbc:postgresql://localhost:5432/snapgram'
export REDIS_HOST=localhost KAFKA_BOOTSTRAP_SERVERS=localhost:9092
# Lần đầu, nếu DB chưa có ADMIN:
export ADMIN_INITIAL_EMAIL='admin@e2e.local'
export ADMIN_INITIAL_USERNAME='admin'
export ADMIN_INITIAL_PASSWORD='Admin1234!'
mvn -o -pl auth-service spring-boot:run
```

```bash
# Terminal 2 — payment-service (port 8087)
set -a && . ./.env && set +a
export DB_URL='jdbc:postgresql://localhost:5439/snapgram'
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export VNPAY_RETURN_URL='http://localhost:8080/api/payments/vnpay/return'
mvn -o -pl payment-service spring-boot:run
```

```bash
# Terminal 3 — api-gateway (port 8080)
set -a && . ./.env && set +a
export REDIS_HOST=localhost
export AUTH_SERVICE_URL='http://localhost:8081'
export PAYMENT_SERVICE_URL='http://localhost:8087'
mvn -o -pl api-gateway spring-boot:run
```

Kiểm tra:

```bash
for p in 8081 8087 8080; do curl -s "http://localhost:$p/actuator/health"; echo " <- $p"; done
```

> Chạy bằng Docker Compose thay vì Maven: sửa `VNPAY_RETURN_URL` trong `.env` thành
> `http://localhost:8080/api/payments/vnpay/return`, rồi
> `cd infra && docker compose up -d --build auth-service payment-service api-gateway`.
> Khi đó bỏ qua 3 terminal Maven ở trên.

Cả 3 phải trả `"status":"UP"`. Nếu `payment-service` báo thiếu bảng `payment_outbox`, kiểm tra Flyway đã chạy V3 (`payment-service` log: `Migrating schema ... to version "3"`).

---

## 2. Đăng ký user + tạo đơn VNPay

```bash
BASE=http://localhost:8080

curl -s -X POST "$BASE/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"username":"e2euser","email":"e2e@test.local","password":"Test1234!","displayName":"E2E User"}' \
  -o /tmp/register.json

# Nếu user đã tồn tại, đổi sang login:
# curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' \
#   -d '{"email":"e2e@test.local","password":"Test1234!"}' -o /tmp/register.json

TOKEN=$(jq -r .accessToken /tmp/register.json)
USERID=$(jq -r .user.id /tmp/register.json)

curl -s -X POST "$BASE/api/payments/vnpay/create" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productCode":"BLUE_TICK"}' \
  -o /tmp/create.json

ORDER=$(jq -r .orderId /tmp/create.json)
PAYURL=$(jq -r .paymentUrl /tmp/create.json)
echo "$ORDER"
echo "$PAYURL"
```

Kết quả mong đợi: HTTP 201, `status=PENDING`, URL bắt đầu bằng
`https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?` và có `vnp_Amount=19900000` (199.000 VND × 100).

---

## 3. Test A — thanh toán thật trên VNPay sandbox

1. Mở `$PAYURL` bằng trình duyệt.
2. Chọn thẻ **NCB**, nhập:
   - Số thẻ: `9704198526191432198`
   - Tên chủ thẻ: `NGUYEN VAN A`
   - Ngày phát hành: `07/15`
   - OTP: `123456`
3. Sau khi thanh toán, VNPay redirect về `VNPAY_RETURN_URL` → gateway 302 tiếp về
   `http://localhost:3000/payment/result?orderId=...&responseCode=00&signatureValid=true`.
   Frontend có thể chưa chạy — chỉ cần nhìn query string trên URL là đủ.
4. **Return URL không đổi trạng thái DB** (theo thiết kế). Đơn vẫn `PENDING`; IPN thật có thể về
   nếu portal đã cấu hình đúng, còn không thì đối chiếu thủ công:

```bash
curl -s -X POST "$BASE/api/auth/admin/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@e2e.local","password":"Admin1234!"}' -o /tmp/admin.json
AT=$(jq -r .accessToken /tmp/admin.json)

curl -s -X POST "$BASE/api/admin/payments/$ORDER/reconcile" \
  -H "Authorization: Bearer $AT" -w '\nHTTP %{http_code}\n'
```

Kết quả mong đợi:

- Đã thanh toán: HTTP 200 + `"status":"SUCCESS"`.
- Chưa thanh toán / VNPay không thấy giao dịch: HTTP 404
  `Transaction not found at VNPAY: Transaction not found` (QueryDR trả `vnp_ResponseCode=91`).
- Giao dịch có nhưng thất bại: HTTP 400 `VNPAY reports transaction not successful: ...`.
- Gọi lại lần 2: HTTP 200, idempotent (đơn đã SUCCESS).

Kiểm tra DB:

```bash
docker exec snapgram-postgres-payment psql -U snapgram -d snapgram -c \
  "select order_id,status,provider_response_code,provider_transaction_no from payment_orders where order_id='$ORDER';"
docker exec snapgram-postgres-payment psql -U snapgram -d snapgram -c \
  "select order_id,status,attempts,last_error from payment_outbox where order_id='$ORDER';"
```

---

## 4. Test B — IPN giả lập (không cần thẻ, kiểm tra cả pipeline)

Mục tiêu: chứng minh `IPN → SUCCESS → outbox → Kafka → auth-service cấp Blue Tick`.
Script ký đúng format VNPay (sort key + URL-encode + HMAC-SHA512) rồi gọi IPN qua gateway:

```bash
cd /home/tphu/Desktop/Tphu/SchoolCode/I2EE/Snapgram-Server
set -a && . ./.env && set +a

ORDER=$(jq -r .orderId /tmp/create.json) python3 - <<'PY'
import hashlib, hmac, json, os, pathlib, urllib.parse
from datetime import datetime
from zoneinfo import ZoneInfo

secret = os.environ['VNPAY_HASH_SECRET']
order = os.environ['ORDER']
pay_date = datetime.now(ZoneInfo('Asia/Ho_Chi_Minh')).strftime('%Y%m%d%H%M%S')
params = {
    'vnp_TmnCode': os.environ['VNPAY_TMN_CODE'],
    'vnp_TxnRef': order,
    'vnp_Amount': '19900000',          # 199.000 VND x100
    'vnp_ResponseCode': '00',
    'vnp_TransactionStatus': '00',
    'vnp_TransactionNo': '14000001',
    'vnp_BankCode': 'NCB',
    'vnp_PayDate': pay_date,
    'vnp_OrderInfo': 'Thanh toan BLUE_TICK',
}
raw = '&'.join(
    f'{urllib.parse.quote_plus(k)}={urllib.parse.quote_plus(v)}'
    for k, v in sorted(params.items())
)
params['vnp_SecureHash'] = hmac.new(
    secret.encode(), raw.encode(), hashlib.sha512).hexdigest()
pathlib.Path('/tmp/ipn.query').write_text(urllib.parse.urlencode(params))
print('payDate=', pay_date)
PY

curl -s "http://localhost:8080/api/payments/vnpay/ipn?$(cat /tmp/ipn.query)" -w '\nHTTP %{http_code}\n'
```

Kết quả mong đợi: `{"Message":"Confirm Success","RspCode":"00"}` + HTTP 200.

Chờ ~5 giây rồi kiểm tra:

```bash
curl -s "$BASE/api/payments/$ORDER" -H "Authorization: Bearer $TOKEN" | jq '{orderId,status}'
docker exec snapgram-postgres-payment psql -U snapgram -d snapgram -tA -c \
  "select status||' | attempts='||attempts from payment_outbox where order_id='$ORDER';"
docker exec snapgram-postgres-auth psql -U snapgram -d snapgram -tA -c \
  "select is_verified||' | '||verified_until from auth_users where id='$USERID';"
docker exec snapgram-postgres-auth psql -U snapgram -d snapgram -tA -c \
  "select event_id||' | '||product_code||' | '||granted_until from auth_payment_processed_events where user_id='$USERID';"
```

Kết quả đã chạy thực tế:

- Order: `SUCCESS`, `provider_response_code=00`, `provider_transaction_no=14000001`.
- Outbox: `SENT`, `attempts=1`.
- Auth: `is_verified=true`, `verified_until` = hôm nay + 30 ngày, có dòng `auth_payment_processed_events`.

> Đơn đã SUCCESS thì **không thanh toán lại URL cũ** — hãy tạo đơn mới cho Test A.

---

## 5. Test C — IPN thật qua tunnel

**Chỉ sửa merchant portal, không sửa `.env`/`application.yml`.** VNPay không nhận IPN URL
trong payment URL; URL IPN gắn với Terminal/TmnCode trên merchant portal.

### 5.1. Bật tunnel

```bash
# Cài: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
cloudflared tunnel --url http://localhost:8080
```

Giữ terminal chạy, lấy URL `https://xxx.trycloudflare.com`. Kiểm tra:

```bash
curl -s "https://xxx.trycloudflare.com/api/payments/vnpay/ipn"; echo
```

Kỳ vọng `{"RspCode":"97","Message":"Invalid signature"}` (gọi thiếu params là đúng).
Nếu 404/502: tunnel sai port (phải `8080`) hoặc gateway chưa chạy.

### 5.2. Cập nhật merchant portal

1. Đăng nhập `https://sandbox.vnpayment.vn/merchantv2/`.
2. Vào `https://sandbox.vnpayment.vn/merchantv2/Account/TerminalEdit.htm`
   (hoặc **Account Information** → chọn website → icon Edit).
3. Chọn đúng Terminal có TmnCode `WTYSCO2I`.
4. Đặt **IPN URL** = `https://xxx.trycloudflare.com/api/payments/vnpay/ipn` → Save.
5. Mở lại trang kiểm tra đã lưu.

Nếu portal không có field/không cho sửa: gửi URL cho VNPay support hoặc email cấp tài khoản sandbox.
Lưu ý: mỗi Terminal một IPN URL; quick tunnel đổi URL mỗi lần chạy; khôi phục URL sau khi test.

### 5.3. Không cần đổi env

- IPN không cần đổi env. Đặt `VNPAY_RETURN_URL=http://localhost:8080/api/payments/vnpay/return`
  trong `.env` hoặc override khi start để browser quay về máy bạn; return không cần tunnel.
- Không còn `VNPAY_IPN_URL` trong code/config — biến này đã bị xóa vì VNPay không dùng.
- Chỉ khi vừa đổi `VNPAY_RETURN_URL` mới cần restart `payment-service`:
  Maven chạy lại process; Docker `docker compose up -d --force-recreate payment-service`
  (vì `env_file` chỉ đọc lúc tạo container). Chỉ đổi portal thì không cần restart.

### 5.4. Test

Tạo đơn mới (đơn cũ đã SUCCESS), thanh toán thẻ NCB, rồi theo dõi:

- Terminal cloudflared: request `GET /api/payments/vnpay/ipn?...` từ VNPay.
- Terminal `payment-service`: dòng `IPN` và `SUCCESS`.
- DB/outbox/auth theo mục 6.

Nếu IPN không về: dùng reconcile (mục 3) — QueryDR độc lập với tunnel. Khi trả `01/04/97/99`
hoặc timeout, VNPay retry tối đa 10 lần, cách nhau 5 phút.

---

## 6. Checklist kết quả

| Lớp | Kiểm tra | Kỳ vọng |
|---|---|---|
| Tạo đơn | `POST /api/payments/vnpay/create` | 201, `PENDING`, URL sandbox đúng chữ ký |
| Return | Mở URL sau thanh toán | 302 về frontend, `signatureValid=true` |
| IPN | `RspCode=00` | 200, đơn `SUCCESS` |
| Đối soát | `POST /api/admin/payments/{id}/reconcile` | 200 idempotent, QueryDR khớp |
| Outbox | bảng `payment_outbox` | `SENT`, `attempts=1` |
| Kafka | topic `payment.completed` | có message `eventId`, `orderId`, `userId` |
| Blue Tick | `auth_users.is_verified` | `true`, `verified_until ≈ now + 30d` |

Xem message trên Kafka:

```bash
docker exec snapgram-kafka-e2e /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic payment.completed --from-beginning --max-messages 1
```

(Nếu dùng Kafka của compose: `docker exec snapgram-kafka ...`.)

---

## 7. Lỗi thường gặp

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| 400 `VNPay is not configured` | Thiếu `VNPAY_TMN_CODE`/`VNPAY_HASH_SECRET` trong môi trường chạy | `set -a; . ./.env; set +a` trước khi start |
| `Invalid signature` | Sai secret, params bị sửa, hoặc encode sai | Ký trên đúng tập `vnp_*` đã sort, URL-encode (`+` cho space) |
| `Merchant code mismatch` | `vnp_TmnCode` khác env | Dùng đúng TMN code sandbox |
| `Missing vnp_TransactionStatus` | IPN tự tạo thiếu field | Thêm field, VNPay thật luôn gửi |
| Reconcile 404 `Transaction not found` | Chưa thanh toán hoặc QueryDR trả 91/02 | Thanh toán xong mới reconcile |
| IPN trả 200 nhưng đơn `PENDING` | Amount lệch / sai chữ ký / order không tồn tại | Xem log `Rejected ... IPN`, đối chiếu `vnp_Amount` |
| Outbox `SENT` nhưng không cấp Blue Tick | Consumer auth không chạy / sai topic | Kiểm tra consumer group `auth-service`, log `Cap Blue Tick` |
| Return URL chết (`trycloudflare` cũ) | `.env` còn tunnel cũ | Override `VNPAY_RETURN_URL` localhost hoặc tạo tunnel mới |
| QueryDR timeout | Mạng / VNPay chậm | Thử lại; timeout cấu hình 10s connect / 30s read |
| Đơn hết hạn sau 15 phút | `payment.pending-timeout-minutes` | Tạo đơn mới; IPN success đến trễ vẫn được chấp nhận |

---

## 8. Dọn dẹp

```bash
# Dừng service Maven: Ctrl+C ở 3 terminal

# Dừng hạ tầng (giữ dữ liệu)
cd infra && docker compose down

# Kafka tạm (nếu dùng cách 2)
docker rm -f snapgram-kafka-e2e

# Reset sạch DB test (XÓA TOÀN BỘ dữ liệu payment/auth)
# docker compose down -v
```

Nếu muốn xóa riêng đơn test:

```bash
docker exec snapgram-postgres-payment psql -U snapgram -d snapgram -c \
  "delete from payment_outbox where order_id='$ORDER';
   delete from payment_event_log where order_id='$ORDER';
   delete from payment_orders where order_id='$ORDER';"
```
