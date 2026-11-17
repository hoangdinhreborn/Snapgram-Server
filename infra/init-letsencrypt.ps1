# ==============================================================================
# SNAPGRAM LETSENCRYPT INITIALIZATION SCRIPT (Windows PowerShell)
# Tự động hóa xin cấp chứng chỉ SSL thật từ Let's Encrypt cho hqdev.ddns.net
# ==============================================================================
param (
    [string]$Email = "",
    [switch]$NoEmail = $false,
    [switch]$Staging = $false
)

$ErrorActionPreference = "Stop"
$Domain = "hqdev.ddns.net"
$RsaKeySize = 4096
$CertbotDir = "./nginx/certbot"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Snapgram Let's Encrypt SSL Initializer (Domain: $Domain)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Dò tìm IP mạng LAN của máy tính hiện tại
$LocalIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254*" -and $_.IPAddress -notlike "172.*" } | Select-Object -ExpandProperty IPAddress -First 1)
if (-not $LocalIp) { $LocalIp = "192.168.10.26" }
Write-Host "🌐 IP mạng LAN máy tính: $LocalIp (Cần Port Forward cổng 80 & 443 về IP này trên Router)" -ForegroundColor Magenta

# Xử lý tham số email cho Certbot (Let's Encrypt không yêu cầu mail server)
if ($NoEmail -or [string]::IsNullOrWhiteSpace($Email)) {
    $EmailArg = "--register-unsafely-without-email"
    Write-Host "ℹ️  Không dùng email nhận thông báo từ Let's Encrypt (--register-unsafely-without-email)." -ForegroundColor Yellow
} else {
    $EmailArg = "--email $Email"
    Write-Host "📧 Email nhận cảnh báo gia hạn Let's Encrypt: $Email" -ForegroundColor Cyan
}

# 1. Đảm bảo thư mục tồn tại & tạo bootstrap cert nếu chưa có
$LiveDir = "$CertbotDir/conf/live/$Domain"
if (-not (Test-Path $LiveDir)) {
    New-Item -ItemType Directory -Force -Path $LiveDir | Out-Null
}
if (-not (Test-Path "$CertbotDir/www")) {
    New-Item -ItemType Directory -Force -Path "$CertbotDir/www" | Out-Null
}
if (-not (Test-Path "$LiveDir/fullchain.pem")) {
    Write-Host "Tạo chứng chỉ bootstrap tạm thời cho Nginx khởi động..." -ForegroundColor Yellow
    docker run --rm --entrypoint sh -v "${PWD}/${LiveDir}:/out" certbot/certbot:latest -c `
      "openssl req -x509 -nodes -newkey rsa:2048 -days 365 -keyout /out/privkey.pem -out /out/fullchain.pem -subj '/CN=$Domain'"
}

# 2. Khởi động Nginx với Bootstrap Certificate nếu chưa chạy
Write-Host "[1/4] Khởi động Nginx service..." -ForegroundColor Green
docker compose up -d nginx

# 3. Chuẩn bị tham số Staging nếu có
$StagingArg = ""
if ($Staging) {
    $StagingArg = "--staging"
    Write-Host "⚠️  Chế độ STAGING đang bật (Dùng để test, Let's Encrypt không tính rate limit)" -ForegroundColor Yellow
}

# 4. Yêu cầu cấp chứng chỉ Let's Encrypt qua Webroot Challenge
Write-Host "[2/4] Gửi yêu cầu xác thực ACME HTTP-01 tới Let's Encrypt..." -ForegroundColor Green
$certbotArgs = @(
    "compose", "run", "--rm", "--entrypoint",
    "certbot certonly --webroot -w /var/www/certbot $StagingArg $EmailArg -d $Domain --rsa-key-size $RsaKeySize --agree-tos --force-renewal --non-interactive",
    "certbot"
)
& docker @certbotArgs

# Kiểm tra kết quả Certbot - Fail-fast nếu thất bại
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "❌ THẤT BẠI: Quá trình xin cấp chứng chỉ Let's Encrypt không thành công!" -ForegroundColor Red
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "Nguyên nhân phổ biến và cách khắc phục:" -ForegroundColor Yellow
    Write-Host "  1. Modem/Router chưa cấu hình Port Forwarding cổng 80 & 443 về máy này (IP LAN: $LocalIp)." -ForegroundColor Yellow
    Write-Host "     -> Đăng nhập vào Router (http://192.168.10.1) -> Port Forwarding / Virtual Server -> trỏ port 80 & 443 TCP về $LocalIp." -ForegroundColor Yellow
    Write-Host "  2. Tên miền '$Domain' chưa cập nhật đúng Public IP hiện tại trên No-IP." -ForegroundColor Yellow
    Write-Host "  3. Tường lửa Windows Defender đang chặn kết nối đến cổng 80 hoặc 443." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "💡 Mẹo: Hãy thử nghiệm với tham số -Staging để tránh bị Let's Encrypt rate-limit do thử lỗi nhiều lần:" -ForegroundColor Cyan
    Write-Host "   .\init-letsencrypt.ps1 -Staging" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Red
    exit 1
}

# 5. Reload Nginx để nạp chứng chỉ mới
Write-Host "[3/4] Nạp chứng chỉ mới vào Nginx..." -ForegroundColor Green
docker compose exec nginx nginx -s reload

Write-Host "[4/4] Khởi động dịch vụ Certbot định kỳ (Auto-renewal)..." -ForegroundColor Green
docker compose up -d certbot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🎉 HOÀN TẤT! Chứng chỉ SSL Let's Encrypt đã sẵn sàng tại:" -ForegroundColor Green
Write-Host "   https://$Domain" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
