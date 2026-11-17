# ==============================================================================
# SNAPGRAM LETSENCRYPT INITIALIZATION SCRIPT (Windows PowerShell)
# Tự động hóa xin cấp chứng chỉ SSL thật từ Let's Encrypt cho hqdev.ddns.net
# ==============================================================================
param (
    [string]$Email = "noreply@hqdev.ddns.net",
    [switch]$Staging = $false
)

$ErrorActionPreference = "Stop"
$Domain = "hqdev.ddns.net"
$RsaKeySize = 4096
$CertbotDir = "./nginx/certbot"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   Snapgram Let's Encrypt SSL Initializer (Domain: $Domain)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($Email -eq "noreply@hqdev.ddns.net") {
    Write-Host "Cảnh báo: Bạn đang dùng email mặc định ($Email)." -ForegroundColor Yellow
    Write-Host "   Khuyên dùng: .\init-letsencrypt.ps1 -Email your-email@gmail.com" -ForegroundColor Yellow
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
    "certbot certonly --webroot -w /var/www/certbot $StagingArg --email $Email -d $Domain --rsa-key-size $RsaKeySize --agree-tos --force-renewal --non-interactive",
    "certbot"
)
& docker @certbotArgs

# 5. Reload Nginx để nạp chứng chỉ mới
Write-Host "[3/4] Nạp chứng chỉ mới vào Nginx..." -ForegroundColor Green
docker compose exec nginx nginx -s reload

Write-Host "[4/4] Khởi động dịch vụ Certbot định kỳ (Auto-renewal)..." -ForegroundColor Green
docker compose up -d certbot

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "🎉 HOÀN TẤT! Chứng chỉ SSL Let's Encrypt đã sẵn sàng tại:" -ForegroundColor Green
Write-Host "   https://$Domain" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan
