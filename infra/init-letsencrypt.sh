#!/bin/bash
set -e

# ==============================================================================
# SNAPGRAM LETSENCRYPT INITIALIZATION SCRIPT (Bash / Linux / WSL)
# Tự động hóa xin cấp chứng chỉ SSL thật từ Let's Encrypt cho hqdev.ddns.net
# ==============================================================================

DOMAIN="hqdev.ddns.net"
RSA_KEY_SIZE=4096
CERTBOT_DIR="./nginx/certbot"
EMAIL="${1:-}" # Truyền email cá nhân qua tham số (VD: your@gmail.com) hoặc để trống
STAGING="${2:-0}" # 1 = Staging (thử nghiệm, tránh rate limit), 0 = Production thật

echo "=========================================================="
echo "   Snapgram Let's Encrypt SSL Initializer (Domain: $DOMAIN)"
echo "=========================================================="

# Xử lý tham số email (Let's Encrypt không cần mail server)
if [ -z "$EMAIL" ] || [ "$EMAIL" = "none" ] || [ "$EMAIL" = "no-email" ]; then
    EMAIL_ARG="--register-unsafely-without-email"
    echo "ℹ️  Không dùng email nhận thông báo từ Let's Encrypt (--register-unsafely-without-email)."
else
    EMAIL_ARG="--email $EMAIL"
    echo "📧 Email nhận cảnh báo gia hạn Let's Encrypt: $EMAIL"
fi

# 1. Kiểm tra cấu trúc thư mục & Bootstrap Certificate
LIVE_DIR="$CERTBOT_DIR/conf/live/$DOMAIN"
mkdir -p "$LIVE_DIR"
mkdir -p "$CERTBOT_DIR/www"

if [ ! -f "$LIVE_DIR/fullchain.pem" ]; then
    echo "Tạo chứng chỉ bootstrap tạm thời cho Nginx khởi động..."
    docker run --rm --entrypoint sh -v "$PWD/$LIVE_DIR:/out" certbot/certbot:latest -c \
      "openssl req -x509 -nodes -newkey rsa:2048 -days 365 -keyout /out/privkey.pem -out /out/fullchain.pem -subj '/CN=$DOMAIN'"
fi

# 2. Khởi động Nginx với Bootstrap Certificate nếu chưa chạy
echo "[1/4] Khởi động Nginx service..."
docker compose up -d nginx

# 3. Chuẩn bị tham số cho Certbot
if [ "$STAGING" != "0" ]; then
    STAGING_ARG="--staging"
    echo "⚠️  Chế độ STAGING đang bật (Dùng để test, Let's Encrypt không tính vào rate limit)"
else
    STAGING_ARG=""
fi

# 4. Yêu cầu cấp chứng chỉ Let's Encrypt qua Webroot Challenge
echo "[2/4] Gửi yêu cầu xác thực ACME HTTP-01 tới Let's Encrypt..."
set +e
docker compose run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    $EMAIL_ARG \
    -d $DOMAIN \
    --rsa-key-size $RSA_KEY_SIZE \
    --agree-tos \
    --force-renewal \
    --non-interactive" certbot
CERTBOT_EXIT=$?
set -e

if [ $CERTBOT_EXIT -ne 0 ]; then
  echo ""
  echo "=========================================================="
  echo "❌ THẤT BẠI: Quá trình xin cấp chứng chỉ Let's Encrypt không thành công!"
  echo "=========================================================="
  echo "Nguyên nhân phổ biến và cách khắc phục:"
  echo "  1. Modem/Router chưa cấu hình Port Forwarding cổng 80 & 443 về máy này."
  echo "     -> Đăng nhập vào Router (http://192.168.10.1) -> Port Forwarding -> trỏ port 80 & 443 TCP về IP máy tính."
  echo "  2. Tên miền '$DOMAIN' chưa cập nhật đúng Public IP hiện tại trên No-IP."
  echo "  3. Tường lửa đang chặn cổng 80 hoặc 443."
  echo ""
  echo "💡 Mẹo: Hãy thử nghiệm với chế độ Staging (tham số thứ 2 là 1) để tránh bị rate limit:"
  echo "   ./init-letsencrypt.sh your-email@gmail.com 1"
  echo "=========================================================="
  exit 1
fi

# 5. Reload Nginx để nạp chứng chỉ mới
echo "[3/4] Nạp chứng chỉ mới vào Nginx..."
docker compose exec nginx nginx -s reload

echo "[4/4] Khởi động dịch vụ Certbot định kỳ (Auto-renewal)..."
docker compose up -d certbot

echo "=========================================================="
echo "🎉 HOÀN TẤT! Chứng chỉ SSL Let's Encrypt đã sẵn sàng tại:"
echo "   https://$DOMAIN"
echo "=========================================================="
