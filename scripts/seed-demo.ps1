<#
.SYNOPSIS
    Script Seed Data Demo cho mạng xã hội Snapgram.
.DESCRIPTION
    Script thực hiện khởi tạo toàn bộ dữ liệu mẫu chân thực qua API Gateway:
    - 21 người dùng (20 user + tài khoản cố định demo/Demo@12345 quyền ADMIN)
    - Cập nhật avatar, bio tiếng Việt, private accounts, notes
    - Quan hệ Follow và duyệt follow request
    - 60 bài viết đa dạng: ảnh đơn, carousel ảnh, video dọc, carousel trộn ảnh+video, chỉ text
    - Tương tác tự nhiên: like, comment, reply đa cấp, like comment, save, video view (watch_time_ratio), skip
    - 15 story: ảnh, video với đủ 3 mức visibility
    - Kích hoạt 2 Spring Batch jobs (trendingPostsJob, userRecommendationJob) của recommender-service
    - Idempotent: chạy lại an toàn, không nhân đôi dữ liệu thông qua scripts/seed/state.json
.PARAMETER BaseUrl
    Địa chỉ API Gateway (mặc định http://localhost:8080)
.PARAMETER DemoPassword
    Mật khẩu mặc định cho các tài khoản demo (mặc định Demo@12345)
.PARAMETER UserCount
    Số lượng user phụ cần seed (mặc định 20, tổng cộng 21 gồm cả demo)
.PARAMETER PostCount
    Số lượng bài viết cần seed (mặc định 60)
.PARAMETER StoryCount
    Số lượng story cần seed (mặc định 15)
.PARAMETER ProbeOnly
    Chỉ kiểm tra kết nối, đăng ký & đăng nhập thử nghiệm, không seed dữ liệu
.PARAMETER Force
    Bỏ qua file state, ép buộc seed mới dữ liệu
.PARAMETER DbContainer
    Tên container PostgreSQL auth (tự dò tìm nếu để trống)
.PARAMETER SkipBatch
    Bỏ qua bước kích hoạt Spring Batch jobs của recommender
#>

[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$DemoPassword = "Demo@12345",
    [int]$UserCount = 20,
    [int]$PostCount = 60,
    [int]$StoryCount = 15,
    [switch]$ProbeOnly,
    [switch]$Force,
    [string]$DbContainer = "",
    [switch]$SkipBatch
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

# Load assembly for .NET HttpClient if available
try {
    Add-Type -AssemblyName System.Net.Http -ErrorAction SilentlyContinue
} catch {}

# ─────────────────────────────────────────────────────────────────────────────
# 1. CẤU HÌNH ENDPOINTS TẬP TRUNG
# ─────────────────────────────────────────────────────────────────────────────
$Endpoints = @{
    # Auth endpoints
    Register            = "$BaseUrl/api/auth/register"
    Login               = "$BaseUrl/api/auth/login"
    MyProfile           = "$BaseUrl/api/users/me"
    UpdatePrivacy       = "$BaseUrl/api/users/me/privacy"
    Note                = "$BaseUrl/api/users/me/note"
    
    # Media endpoints
    UploadMedia         = "$BaseUrl/api/media/upload"
    GetMedia            = "$BaseUrl/api/media/{id}"
    
    # Content endpoints
    Posts               = "$BaseUrl/api/posts"
    PostInteract        = "$BaseUrl/api/posts/{postId}/interact"
    PostComments        = "$BaseUrl/api/posts/{postId}/comments"
    CommentLike         = "$BaseUrl/api/posts/{postId}/comments/{commentId}/like"
    SavedPosts          = "$BaseUrl/api/saved/{postId}"
    FollowUser          = "$BaseUrl/api/follows/{userId}"
    FollowRequests      = "$BaseUrl/api/follows/requests"
    AcceptFollow        = "$BaseUrl/api/follows/requests/{followId}/accept"
    Stories             = "$BaseUrl/api/stories"
    
    # Recommender batch endpoints
    RunBatchJob         = "$BaseUrl/api/admin/batch/jobs/{jobName}/run"
    GetBatchExecution   = "$BaseUrl/api/admin/batch/executions/{id}"
    GetTrending         = "$BaseUrl/api/recommendations/trending"
}

# Thư mục lưu trữ assets và state
$ScriptDir    = $PSScriptRoot
if (-not $ScriptDir) { $ScriptDir = (Get-Location).Path }
$SeedDir      = Join-Path $ScriptDir "seed"
$AvatarDir    = Join-Path $SeedDir "avatars"
$PostImgDir   = Join-Path $SeedDir "posts"
$StoryImgDir  = Join-Path $SeedDir "stories"
$VideoDir     = Join-Path $SeedDir "videos"
$StateFile    = Join-Path $SeedDir "state.json"

# Random generator có seed cố định 42 để đảm bảo tính tái lập (reproducible)
$Script:Rng = [System.Random]::new(42)

# Bộ đếm tổng kết
$Summary = @{
    UsersCreated     = 0
    UsersExisting    = 0
    ProfilesUpdated  = 0
    PrivateUsers     = 0
    NotesCreated     = 0
    FollowsCreated   = 0
    FollowsAccepted  = 0
    MediaUploaded    = 0
    PostsCreated     = 0
    LikesCreated     = 0
    CommentsCreated  = 0
    RepliesCreated   = 0
    CommentLikes     = 0
    SavesCreated     = 0
    ViewsCreated     = 0
    SkipsCreated     = 0
    StoriesCreated   = 0
    BatchJobsRun     = 0
    SkippedItems     = @()
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. HELPER FUNCTIONS: HTTP, RETRY, FAIL-FAST, JWT
# ─────────────────────────────────────────────────────────────────────────────

function Write-Step {
    param([string]$Message)
    Write-Host "`n====> $Message" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "  [OK] $Message" -ForegroundColor Green
}

function Write-WarnMsg {
    param([string]$Message)
    Write-Host "  [WARN] $Message" -ForegroundColor Yellow
}

function Write-ErrMsg {
    param([string]$Message)
    Write-Host "  [FAIL] $Message" -ForegroundColor Red
}

# Giải mã Base64URL JWT payload lấy claims
function Get-JwtPayload {
    param([string]$Token)
    try {
        if (-not $Token) { return $null }
        $parts = $Token.Split('.')
        if ($parts.Length -lt 2) { return $null }
        $b64 = $parts[1].Replace('-', '+').Replace('_', '/')
        switch ($b64.Length % 4) {
            2 { $b64 += '==' }
            3 { $b64 += '=' }
        }
        $bytes = [System.Convert]::FromBase64String($b64)
        $json = [System.Text.Encoding]::UTF8.GetString($bytes)
        return $json | ConvertFrom-Json
    } catch {
        return $null
    }
}

# Helper gọi API có retry 429/5xx, in body server lỗi và hỗ trợ Fail-fast
function Invoke-SnapgramApi {
    param(
        [string]$Url,
        [string]$Method = "GET",
        [object]$Body = $null,
        [string]$Token = "",
        [int]$MaxRetries = 3
    )

    $headers = @{
        "Accept" = "application/json"
    }
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    $jsonBody = $null
    if ($Body -ne $null) {
        if ($Body -is [string]) {
            $jsonBody = $Body
        } else {
            $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
        }
        $headers["Content-Type"] = "application/json; charset=utf-8"
    }

    $attempt = 0
    while ($attempt -lt $MaxRetries) {
        $attempt++
        try {
            $params = @{
                Uri         = $Url
                Method      = $Method
                Headers     = $headers
                ErrorAction = "Stop"
            }
            if ($jsonBody -ne $null -and $Method -ne "GET" -and $Method -ne "DELETE") {
                $params["Body"] = [System.Text.Encoding]::UTF8.GetBytes($jsonBody)
            }

            $response = Invoke-RestMethod @params
            return $response
        } catch {
            $statusCode = 0
            $errorBody = ""

            if ($_.Exception.Response) {
                try {
                    $statusCode = [int]$_.Exception.Response.StatusCode
                    $stream = $_.Exception.Response.GetResponseStream()
                    if ($stream) {
                        $reader = [System.IO.StreamReader]::new($stream)
                        $errorBody = $reader.ReadToEnd()
                    }
                } catch {}
            } elseif ($_.ErrorDetails -and $_.ErrorDetails.Message) {
                $errorBody = $_.ErrorDetails.Message
            } else {
                $errorBody = $_.Exception.Message
            }

            # Retry nếu bị rate limit (429) hoặc server error (500, 502, 503, 504)
            if ($statusCode -eq 429 -or ($statusCode -ge 500 -and $statusCode -le 504)) {
                if ($attempt -lt $MaxRetries) {
                    $sleepSec = $attempt * 2
                    Write-WarnMsg "API $Method $Url trả về HTTP $($statusCode). Đang retry lần $attempt/$MaxRetries sau ${sleepSec}s..."
                    Start-Sleep -Seconds $sleepSec
                    continue
                }
            }

            # Ném exception kèm thông tin chi tiết
            $msg = "API $Method $Url thất bại với HTTP $($statusCode): $errorBody"
            $ex = [System.InvalidOperationException]::new($msg, $_.Exception)
            $ex.Data["StatusCode"] = $statusCode
            $ex.Data["ErrorBody"]  = $errorBody
            $ex.Data["Url"]        = $Url
            $ex.Data["Method"]     = $Method
            throw $ex
        }
    }
}

# Helper upload multipart file sử dụng HttpClient
function Upload-MediaFile {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$Token
    )

    if (-not (Test-Path $FilePath)) {
        throw "File không tồn tại: $FilePath"
    }

    $fileName = [System.IO.Path]::GetFileName($FilePath)
    $ext = [System.IO.Path]::GetExtension($FilePath).ToLowerInvariant()
    $mimeType = switch ($ext) {
        ".jpg"  { "image/jpeg" }
        ".jpeg" { "image/jpeg" }
        ".png"  { "image/png" }
        ".webp" { "image/webp" }
        ".gif"  { "image/gif" }
        ".mp4"  { "video/mp4" }
        ".mov"  { "video/quicktime" }
        ".webm" { "video/webm" }
        default { "application/octet-stream" }
    }

    # Thử dùng .NET HttpClient trước
    try {
        $client = [System.Net.Http.HttpClient]::new()
        $client.Timeout = [System.TimeSpan]::FromSeconds(60)
        if ($Token) {
            $client.DefaultRequestHeaders.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
        }

        $form = [System.Net.Http.MultipartFormDataContent]::new()
        $fileBytes = [System.IO.File]::ReadAllBytes($FilePath)
        $fileContent = [System.Net.Http.ByteArrayContent]::new($fileBytes)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($mimeType)
        $form.Add($fileContent, "file", $fileName)

        $httpResponse = $client.PostAsync($Url, $form).GetAwaiter().GetResult()
        $responseContent = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $client.Dispose()

        if (-not $httpResponse.IsSuccessStatusCode) {
            throw "Upload HTTP $([int]$httpResponse.StatusCode): $responseContent"
        }

        return ($responseContent | ConvertFrom-Json)
    } catch {
        # Fallback dùng curl.exe nếu có
        $curlCmd = Get-Command curl.exe -ErrorAction SilentlyContinue
        if ($curlCmd) {
            $tmpOut = [System.IO.Path]::GetTempFileName()
            $curlArgs = @("-s", "-X", "POST", $Url, "-H", "Authorization: Bearer $Token", "-F", "file=@$FilePath;type=$mimeType", "-o", $tmpOut, "-w", "%{http_code}")
            $code = & curl.exe @curlArgs
            $content = Get-Content -Raw $tmpOut -ErrorAction SilentlyContinue
            Remove-Item $tmpOut -ErrorAction SilentlyContinue
            if ($code -ge 200 -and $code -lt 300) {
                return ($content | ConvertFrom-Json)
            }
            throw "curl.exe upload HTTP $($code): $content"
        }
        throw $_
    }
}

# Kiểm tra và quản lý Fail-fast trong từng bước
function Assert-PhaseSuccess {
    param(
        [string]$PhaseName,
        [int]$SuccessCount,
        [int]$ErrorCount,
        [string]$LastEndpoint = ""
    )
    if ($SuccessCount -eq 0 -and $ErrorCount -ge 3) {
        Write-ErrMsg "FAIL-FAST: Giai đoạn '$PhaseName' gặp lỗi liên tiếp ($ErrorCount lần) mà không có request nào thành công!"
        if ($LastEndpoint) { Write-ErrMsg "Endpoint nghi vấn: $LastEndpoint" }
        Write-ErrMsg "Dừng toàn bộ script để tránh làm sai lệch hệ thống."
        exit 1
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 3. QUẢN LÝ IDEMPOTENT STATE
# ─────────────────────────────────────────────────────────────────────────────

function Load-State {
    if (Test-Path $StateFile -and (-not $Force)) {
        try {
            $json = Get-Content -Raw $StateFile -Encoding UTF8
            return ($json | ConvertFrom-Json)
        } catch {
            Write-WarnMsg "Không thể đọc file state cũ. Khởi tạo state mới."
        }
    }
    return [PSCustomObject]@{
        CompletedSteps = @()
        Users          = @()
        Posts          = @()
        Stories        = @()
        CreatedAt      = (Get-Date).ToString("o")
    }
}

function Save-State {
    param($StateObj)
    try {
        if (-not (Test-Path $SeedDir)) {
            New-Item -ItemType Directory -Path $SeedDir -Force | Out-Null
        }
        $json = $StateObj | ConvertTo-Json -Depth 10
        [System.IO.File]::WriteAllText($StateFile, $json, [System.Text.Encoding]::UTF8)
    } catch {
        Write-WarnMsg "Không thể lưu state: $($_.Exception.Message)"
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 4. CHUẨN BỊ ASSETS MẪU (ẢNH, VIDEO DỌC)
# ─────────────────────────────────────────────────────────────────────────────

function Ensure-AssetDirectories {
    foreach ($dir in @($SeedDir, $AvatarDir, $PostImgDir, $StoryImgDir, $VideoDir)) {
        if (-not (Test-Path $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
    }
}

function Download-FileWithRetry {
    param([string]$Url, [string]$OutPath, [int]$Retries = 3)
    if (Test-Path $OutPath) { return $true }
    
    for ($i = 1; $i -le $Retries; $i++) {
        try {
            Invoke-WebRequest -Uri $Url -OutFile $OutPath -TimeoutSec 15 -ErrorAction Stop
            if ((Get-Item $OutPath).Length -gt 100) {
                return $true
            }
        } catch {
            if ($i -eq $Retries) {
                Write-WarnMsg "Tải $Url thất bại sau $Retries lần: $($_.Exception.Message)"
                return $false
            }
            Start-Sleep -Seconds 1
        }
    }
    return $false
}

function Prepare-Assets {
    Write-Step "Chuẩn bị thư mục và tải assets mẫu (Avatar, Post, Story, Video)"
    Ensure-AssetDirectories

    # 1. Avatars (1..20)
    Write-Host "  -> Đang kiểm tra / tải avatars (1..20)..." -NoNewline
    $avatarSuccess = 0
    for ($i = 1; $i -le 20; $i++) {
        $path = Join-Path $AvatarDir "avatar_$i.jpg"
        $url = "https://i.pravatar.cc/300?img=$i"
        if (Download-FileWithRetry -Url $url -OutPath $path) { $avatarSuccess++ }
    }
    Write-Host " $avatarSuccess/20 hoàn thành." -ForegroundColor Green

    # 2. Post images (1..60)
    Write-Host "  -> Đang kiểm tra / tải ảnh bài viết (1..60)..." -NoNewline
    $postImgSuccess = 0
    for ($i = 1; $i -le 60; $i++) {
        $path = Join-Path $PostImgDir "post_$i.jpg"
        $url = "https://picsum.photos/seed/post$i/1080/1080"
        if (Download-FileWithRetry -Url $url -OutPath $path) { $postImgSuccess++ }
    }
    Write-Host " $postImgSuccess/60 hoàn thành." -ForegroundColor Green

    # 3. Story images (1..15)
    Write-Host "  -> Đang kiểm tra / tải ảnh story (1..15)..." -NoNewline
    $storyImgSuccess = 0
    for ($i = 1; $i -le 15; $i++) {
        $path = Join-Path $StoryImgDir "story_$i.jpg"
        $url = "https://picsum.photos/seed/story$i/1080/1920"
        if (Download-FileWithRetry -Url $url -OutPath $path) { $storyImgSuccess++ }
    }
    Write-Host " $storyImgSuccess/15 hoàn thành." -ForegroundColor Green

    # 4. Videos dọc 8s bằng ffmpeg (1..12)
    Write-Host "  -> Đang kiểm tra ffmpeg để tạo 12 video dọc 8s (1080x1920)..."
    $ffmpegCmd = Get-Command ffmpeg -ErrorAction SilentlyContinue
    $Script:HasFfmpeg = ($ffmpegCmd -ne $null)

    if ($Script:HasFfmpeg) {
        $vidCreated = 0
        for ($i = 1; $i -le 12; $i++) {
            $vidPath = Join-Path $VideoDir "video_$i.mp4"
            if (-not (Test-Path $vidPath)) {
                $freq = 300 + ($i * 50)
                # Tạo video dọc 1080x1920 8s, h264/aac, dung lượng ~1.8MB
                $ffArgs = @(
                    "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1080x1920:rate=30",
                    "-f", "lavfi", "-i", "sine=frequency=$($freq):sample_rate=44100",
                    "-t", "8",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-b:v", "1500k",
                    "-c:a", "aac", "-b:a", "128k",
                    "-shortest",
                    $vidPath
                )
                & ffmpeg @ffArgs *>$null
            }
            if (Test-Path $vidPath) { $vidCreated++ }
        }
        Write-Success "Đã tạo/kiểm tra $vidCreated/12 video dọc bằng FFmpeg."
    } else {
        Write-WarnMsg "Không tìm thấy ffmpeg trên máy. Các bài viết video sẽ tự động fallback sang ảnh tương ứng."
        $Summary.SkippedItems += "Tạo video bằng ffmpeg (ffmpeg không có trong PATH, fallback sang ảnh)"
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 5. DỮ LIỆU CỐ ĐỊNH TIẾNG VIỆT
# ─────────────────────────────────────────────────────────────────────────────

$VietnameseProfiles = @(
    @{ Name = "Nguyễn Minh Tuấn"; Bio = "Đam mê nhiếp ảnh và du lịch 📸 Chụp cuộc sống qua ống kính 🌿"; Web = "https://tuannm.photo"; Pronouns = "he/him"; Cat = "Nhiếp ảnh gia" },
    @{ Name = "Trần Thị Mai";     Bio = "Food reviewer tại Hà Nội & Sài Gòn 🍜 Ăn sập mọi ngõ ngách"; Web = "https://maifoodie.blog"; Pronouns = "she/her"; Cat = "Ẩm thực & Đồ uống" },
    @{ Name = "Lê Hoàng Long";    Bio = "Software Engineer & Tech Enthusiast 💻 Cà phê và những dòng code"; Web = "https://longle.dev"; Pronouns = "he/him"; Cat = "Công nghệ số" },
    @{ Name = "Phạm Hải Đăng";   Bio = "Fitness & Healthy Lifestyle 💪 Mỗi ngày một chút tiến bộ vượt bậc"; Web = "https://dangfitness.vn"; Pronouns = "he/him"; Cat = "Sức khỏe & Thể hình" },
    @{ Name = "Vũ Phương Anh";    Bio = "Digital Creator & Storyteller ✨ Kết nối những câu chuyện thú vị"; Web = "https://phuonganh.art"; Pronouns = "she/her"; Cat = "Sáng tạo nội dung" },
    @{ Name = "Đặng Quốc Bảo";    Bio = "Yêu sách, trà chiều và những giai điệu acoustic bình yên ☕📖"; Web = "https://baodang.me"; Pronouns = "he/him"; Cat = "Tác giả / Blog" },
    @{ Name = "Bùi Thu Hà";       Bio = "Kiến trúc sư trẻ | Thiết kế không gian sống tối giản & hiện đại 🏛️"; Web = "https://hadesign.vn"; Pronouns = "she/her"; Cat = "Kiến trúc & Thiết kế" },
    @{ Name = "Đỗ Minh Khang";    Bio = "Khởi nghiệp công nghệ | Chia sẻ kiến thức và hành trình mỗi ngày 🚀"; Web = "https://khangdo.tech"; Pronouns = "he/him"; Cat = "Kinh doanh" },
    @{ Name = "Hồ Bích Ngọc";     Bio = "Thời trang bền vững & Phong cách sống xanh 🌱 Less is more"; Web = "https://bichngoc.style"; Pronouns = "she/her"; Cat = "Thời trang" },
    @{ Name = "Ngô Thành Nam";    Bio = "Nhạc sĩ độc lập | Những giai điệu mộc mạc từ trái tim 🎸"; Web = "https://namngo.music"; Pronouns = "he/him"; Cat = "Âm nhạc" },
    @{ Name = "Dương Thu Trang";  Bio = "Yêu hoa, gốm và nghệ thuật thủ công truyền thống Việt Nam 🌸"; Web = "https://trangduong.craft"; Pronouns = "she/her"; Cat = "Nghệ thuật" },
    @{ Name = "Lý Gia Hưng";      Bio = "Chuyên gia dữ liệu & Trí tuệ nhân tạo 📊 Data never lies"; Web = "https://hungly.ai"; Pronouns = "he/him"; Cat = "Khoa học dữ liệu" },
    @{ Name = "Võ Diệu Linh";     Bio = "Du lịch trải nghiệm, khám phá văn hóa bản địa vùng cao 🏔️"; Web = "https://linhtravel.net"; Pronouns = "she/her"; Cat = "Du lịch" },
    @{ Name = "Phan Nhật Huy";    Bio = "Barista & Rang cà phê thủ công ☕ Hương vị từ cao nguyên"; Web = "https://huycoffee.vn"; Pronouns = "he/him"; Cat = "Ẩm thực" },
    @{ Name = "Hoàng Kim Oanh";   Bio = "Giáo viên Yoga & Thiền chánh niệm 🧘‍♀️ Tìm lại sự an yên bên trong"; Web = "https://oanhyoga.com"; Pronouns = "she/her"; Cat = "Phong cách sống" },
    @{ Name = "Trịnh Anh Tuấn";   Bio = "Nhiếp ảnh đường phố Hà Nội | Lưu giữ ký ức phố cổ qua từng khung hình 🛵"; Web = "https://tuanstreet.vn"; Pronouns = "he/him"; Cat = "Nhiếp ảnh" },
    @{ Name = "Mai Phương Thảo";  Bio = "Bác sĩ thú y | Yêu thương động vật và thiên nhiên hoang dã 🐾"; Web = "https://thaovet.org"; Pronouns = "she/her"; Cat = "Thú cưng" },
    @{ Name = "Lâm Khánh Chi";    Bio = "Họa sĩ vẽ tranh màu nước & minh họa sách thiếu nhi 🎨"; Web = "https://chibook.art"; Pronouns = "she/her"; Cat = "Hội họa" },
    @{ Name = "Đinh Đức Trọng";   Bio = "Kỹ sư cơ khí, chế tạo và xe cổ 🏎️ Đam mê tốc độ"; Web = "https://trongdinh.auto"; Pronouns = "he/him"; Cat = "Xe cộ" },
    @{ Name = "Cao Thùy Dung";    Bio = "Dược sĩ & Chăm sóc sắc đẹp khoa học 🌿 Khoa học làn da"; Web = "https://dungcosmetics.vn"; Pronouns = "she/her"; Cat = "Sắc đẹp" }
)

$DemoProfile = @{
    Name     = "Snapgram Demo Admin"
    Bio      = "Tài khoản trải nghiệm & Quản trị hệ sinh thái Snapgram 🚀 Khám phá mạng xã hội thế hệ mới"
    Web      = "https://snapgram.local"
    Pronouns = "they/them"
    Cat      = "Quản trị viên"
}

$PostCaptions = @(
    "Hoàng hôn chiều nay tại Hồ Tây đẹp đến nao lòng. Bình yên đôi khi chỉ là một góc nhỏ tĩnh lặng sau ngày dài bận rộn 🌅 #hanoi #sunset #peace",
    "Ghé thăm quán cà phê nhỏ ẩn mình trong ngõ phố cổ. Một ly bạc xỉu thơm ngậy và chút nhạc acoustic nhẹ nhàng ☕ #coffeetime #chill #weekend",
    "Những chuyến đi luôn mang lại cho chúng ta góc nhìn mới mẻ về cuộc đời. Hẹn gặp lại Đà Lạt mù sương! 🌲🍃 #dalat #travelvietnam #wanderlust",
    "Hoàn thành xong dự án lớn sau chuỗi ngày thức khuya dậy sớm. Cảm ơn cả team vì đã cùng nhau nỗ lực hết mình! 💻🚀 #teamwork #coding #developer",
    "Một ngày cuối tuần bình yên bên trang sách yêu thích và tách trà sen thơm ngát 📖🍵 #reading #mindfulness #slowliving",
    "Sài Gòn ngày nắng đẹp rạng rỡ. Dạo bước qua những con đường rợp bóng cây cổ thụ giữa lòng thành phố 🏙️☀️ #saigon #citywalk #vietnam",
    "Bát phở bò gia truyền nóng hổi sớm mai. Hương hồi quế quyện vào từng thìa nước dùng đậm đà 🍜 #vietnamesefood #pho #streetfood",
    "Chuyến trekking băng rừng nguyên sinh cuối tuần. Vượt qua những con dốc gập ghềnh để chạm tới đỉnh mây ⛰️🥾 #trekking #nature #adventure",
    "Bộ ảnh chân dung chụp ngẫu hứng lúc chiều buông. Ánh sáng vàng dịu dàng là món quà tuyệt nhất của thiên nhiên 📷✨ #portrait #photography #goldenhour",
    "Cắm trại đêm bên bờ hồ vắng. Đốt lửa sưởi ấm và ngắm nhìn dải ngân hà lấp lánh trên cao 🔥⛺🌌 #camping #stargazing #chill",
    "Nấu bữa cơm gia đình giản dị nhưng tràn ngập tiếng cười và sự ấm áp 🍳🥘 #homecooking #familytime #cozy",
    "Góc làm việc gọn gàng cho một tuần mới đầy năng suất và ý tưởng sáng tạo 🖥️🪴 #workspace #desksetup #minimalism",
    "Bình minh trên biển Phú Quốc rực rỡ như một bức tranh sơn dầu sống động 🌊🏖️ #phuquoc #sunrise #beachlife",
    "Lạc bước vào triển lãm nghệ thuật đương đại cuối tuần. Mỗi tác phẩm đều gửi gắm một thông điệp sâu lắng 🎨🖼️ #artgallery #contemporary #creative",
    "Đạp xe quanh bờ hồ sáng sớm để hít thở bầu không khí trong lành của mùa thu 🚴‍♂️🍂 #cycling #morningvibes #autumn",
    "Mùa hoa dã quỳ vàng rực rỡ đã về trên những cung đường đèo cao nguyên 🌼🛵 #daquy #dalattrip #flowerseason",
    "Những khoảnh khắc chân thực nhất của cuộc sống đời thường luôn là điều khiến trái tim rung động ❤️📸 #streetphotography #life #moments",
    "Review chi tiết 5 món ăn vặt không thể bỏ qua khi ghé thăm chợ đêm ẩm thực 🍢🧋 #foodie #streetfoodlover #review",
    "Tập luyện thể thao không chỉ vì vóc dáng mà còn vì một tinh thần minh mẫn và tràn đầy sức sống 🏋️‍♀️🧘‍♂️ #fitness #healthy #workout",
    "Giai điệu guitar mộc mạc cho buổi chiều mưa rả rích bên hiên cửa sổ 🌧️🎸 #acoustic #rainyday #vibes"
)

$CommentBank = @(
    "Ảnh chụp góc này đỉnh quá bạn ơi! Tone màu rất ưng mắt 👏",
    "Địa điểm này ở đâu thế ạ, nhìn thơ mộng quá!",
    "Bình yên thực sự, nhìn bức ảnh mà thấy lòng nhẹ nhõm hẳn ❤️",
    "Nội dung chia sẻ rất hữu ích, cảm ơn bạn nhiều nhé!",
    "Chuyến đi tuyệt vời quá, chúc bạn luôn giữ năng lượng tích cực này nhé 🌟",
    "Đẹp xuất sắc luôn! Đúng chất Snapgram nghệ thuật ✨",
    "Màu ảnh trông cuốn thật sự, dùng máy gì chụp vậy bạn?",
    "Quán cà phê này mình cũng từng đi, bánh sừng bò ở đây siêu ngon 🥐☕",
    "Truyền cảm hứng rất nhiều cho mình. Cố gắng phát huy nhé bạn ơi! 🚀",
    "Tuyệt cú mèo! Nhất định phải rủ hội bạn thân ghé đây một lần."
)

$ReplyBank = @(
    "Cảm ơn bạn nhiều nha! Mình chụp bằng máy ảnh mirrorless lúc tầm 5h chiều ấy ạ.",
    "Ở Đà Lạt bạn ơi, homestay gần đồi chè Cầu Đất view siêu xịn xò!",
    "Dạ cảm ơn bạn đã đồng cảm! Chúc bạn tuần mới nhiều niềm vui nha ❤️",
    "Mình chỉnh màu qua Lightroom mobile với công thức tone ấm cổ điển ấy ạ.",
    "Rất vui vì bài viết hữu ích với bạn! Hãy cùng chia sẻ đam mê nhé."
)

$UserNotes = @(
    "Đang nghe nhạc lofi chill 🎧",
    "Cần một ly cà phê đậm đặc ngay lúc này ☕",
    "Hôm nay trời đẹp quá, ai cà phê không? 🌤️",
    "Chạy deadline xuyên màn đêm 🏃‍♂️💻",
    "Ready for the weekend! 🎉",
    "Nghỉ ngơi và sạc lại năng lượng 🔋",
    "Đang tập trung sáng tạo nội dung mới ✨",
    "Chờ đợi một chuyến đi xa ✈️"
)

# ─────────────────────────────────────────────────────────────────────────────
# 6. BƯỚC 1: ĐĂNG KÝ VÀ ĐĂNG NHẬP (PROBE / SEED USERS)
# ─────────────────────────────────────────────────────────────────────────────

function Step-CreateAndLoginUsers {
    param($StateObj)
    Write-Step "Bước 1: Đăng ký & Đăng nhập 20 user + tài khoản 'demo'"

    $userList = @()
    # Danh sách: demo trước, sau đó là user1..user20
    $targets = @("demo") + (1..$UserCount | ForEach-Object { "user$_" })

    $successCount = 0
    $errorCount   = 0

    foreach ($uname in $targets) {
        $email = "$uname@demo.local"
        $displayName = if ($uname -eq "demo") { $DemoProfile.Name } else {
            $idx = [int]($uname -replace '\D') - 1
            if ($idx -ge 0 -and $idx -lt $VietnameseProfiles.Count) { $VietnameseProfiles[$idx].Name } else { "User $uname" }
        }

        # Kiểm tra xem user đã có trong state hay chưa
        $existing = $StateObj.Users | Where-Object { $_.Username -eq $uname } | Select-Object -First 1
        if ($existing -and (-not $Force)) {
            # Thử lấy lại token bằng login
            try {
                $loginBody = @{ email = $email; password = $DemoPassword }
                $authRes = Invoke-SnapgramApi -Url $Endpoints.Login -Method "POST" -Body $loginBody
                $existing.AccessToken = $authRes.accessToken
                $existing.UserId = $authRes.user.id
                $userList += $existing
                $Summary.UsersExisting++
                Write-Host "  [EXISTING] $uname ($email) - Đăng nhập lại thành công." -ForegroundColor Gray
                continue
            } catch {
                Write-WarnMsg "Không thể đăng nhập lại user cũ $uname, sẽ thực hiện tạo mới/đăng nhập lại."
            }
        }

        # 1. Thử Register
        $registered = $false
        try {
            $regBody = @{
                username    = $uname
                email       = $email
                password    = $DemoPassword
                displayName = $displayName
            }
            $authRes = Invoke-SnapgramApi -Url $Endpoints.Register -Method "POST" -Body $regBody
            $userId = $authRes.user.id
            $token  = $authRes.accessToken
            $registered = $true
            $Summary.UsersCreated++
            Write-Success "Đăng ký mới: $uname ($email) -> userId=$userId"
            $successCount++
        } catch {
            $statusCode = $_.Exception.Data["StatusCode"]
            # Nếu đã tồn tại (400 hoặc 409) -> Đăng nhập
            if ($statusCode -eq 400 -or $statusCode -eq 409) {
                try {
                    $loginBody = @{ email = $email; password = $DemoPassword }
                    $authRes = Invoke-SnapgramApi -Url $Endpoints.Login -Method "POST" -Body $loginBody
                    $userId = $authRes.user.id
                    $token  = $authRes.accessToken
                    $Summary.UsersExisting++
                    Write-Host "  [EXISTING] $uname ($email) đã có sẵn -> Đăng nhập thành công (userId=$userId)" -ForegroundColor DarkGreen
                    $successCount++
                } catch {
                    $errorCount++
                    Write-ErrMsg "Không thể đăng nhập $uname sau khi báo trùng: $($_.Exception.Message)"
                }
            } else {
                $errorCount++
                Write-ErrMsg "Đăng ký $uname thất bại: $($_.Exception.Message)"
            }
        }

        if ($userId -and $token) {
            $userEntry = [PSCustomObject]@{
                Username    = $uname
                Email       = $email
                DisplayName = $displayName
                UserId      = $userId
                AccessToken = $token
                IsPrivate   = $false
            }
            $userList += $userEntry
        }

        Assert-PhaseSuccess -PhaseName "Register/Login Users" -SuccessCount $successCount -ErrorCount $errorCount -LastEndpoint $Endpoints.Register
    }

    $StateObj.Users = $userList
    Save-State -StateObj $StateObj
    Write-Success "Tổng kết User: Tạo mới $($Summary.UsersCreated), Đã có $($Summary.UsersExisting), Tổng hoạt động $($userList.Count)"
    return $userList
}

# ─────────────────────────────────────────────────────────────────────────────
# 7. BƯỚC 2: NGOẠI LỆ SQL (VERIFY EMAIL, ELEVATE ADMIN, TICK XANH)
# ─────────────────────────────────────────────────────────────────────────────

function Step-ExecuteSqlExceptions {
    param($StateObj)
    Write-Step "Bước 2: Thực thi ngoại lệ SQL (Verify email %@demo.local, quyền ADMIN và Tick xanh cho 'demo')"

    $dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCmd) {
        Write-WarnMsg "Không tìm thấy Docker CLI trong PATH. Bỏ qua bước SQL tự động."
        Write-Host "Vui lòng tự chạy câu lệnh SQL sau vào container PostgreSQL auth:" -ForegroundColor Yellow
        Write-Host "UPDATE auth_users SET email_verified = true WHERE email LIKE '%@demo.local';" -ForegroundColor Gray
        Write-Host "UPDATE auth_users SET role = 'ADMIN', is_verified = true, verified_until = NOW() + INTERVAL '365 days' WHERE username = 'demo';" -ForegroundColor Gray
        Write-Host "INSERT INTO auth_user_roles (id, user_id, role, created_at) SELECT gen_random_uuid(), id, 'ADMIN', NOW() FROM auth_users WHERE username = 'demo' ON CONFLICT (user_id, role) DO NOTHING;" -ForegroundColor Gray
        $Summary.SkippedItems += "Thực thi SQL qua Docker (Docker CLI không khả dụng)"
        return
    }

    $container = $DbContainer
    if (-not $container) {
        try {
            $running = docker ps --format "{{.Names}}" 2>$null
            $container = ($running -split "`n" | Where-Object { $_ -match "postgres-auth" } | Select-Object -First 1)
        } catch {}
    }
    if (-not $container) {
        $container = "snapgram-postgres-auth"
    }

    $sqlScript = @"
-- 1. Xác thực email cho toàn bộ user demo
UPDATE auth_users 
SET email_verified = true 
WHERE email LIKE '%@demo.local';

-- 2. Nâng tài khoản 'demo' lên ADMIN trong auth_users và auth_user_roles
UPDATE auth_users 
SET role = 'ADMIN' 
WHERE username = 'demo';

INSERT INTO auth_user_roles (id, user_id, role, created_at)
SELECT gen_random_uuid(), id, 'ADMIN', NOW()
FROM auth_users 
WHERE username = 'demo'
ON CONFLICT (user_id, role) DO NOTHING;

-- 3. Cấp tick xanh (verified badge) cho tài khoản 'demo'
UPDATE auth_users 
SET is_verified = true, 
    verified_until = NOW() + INTERVAL '365 days'
WHERE username = 'demo';
"@

    Write-Host "  -> Đang thực thi SQL trên container '$container'..." -ForegroundColor Cyan
    try {
        $res = docker exec -i $container psql -U snapgram -d snapgram -c $sqlScript 2>&1
        if ($LASTEXITCODE -eq 0) {
            Write-Success "SQL cập nhật thành công: Đã verify email %@demo.local, gán ADMIN & tick xanh cho demo."
        } else {
            Write-WarnMsg "Lỗi khi chạy psql: $res"
            $Summary.SkippedItems += "SQL update psql thất bại: $res"
        }
    } catch {
        Write-WarnMsg "Không thể kết nối container $($container): $($_.Exception.Message)"
        $Summary.SkippedItems += "Docker exec psql thất bại: $($_.Exception.Message)"
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 8. BƯỚC 3 & 4: PROFILE UPDATE, AVATAR, PRIVATE, NOTES
# ─────────────────────────────────────────────────────────────────────────────

function Step-UpdateProfilesAndAvatars {
    param($Users, $StateObj)
    Write-Step "Bước 4: Cập nhật Avatar, Bio, Trạng thái Private và Note"

    # Chọn 3 user ngẫu nhiên (không phải demo) làm Private
    $nonDemo = $Users | Where-Object { $_.Username -ne "demo" }
    $privateUsernames = @()
    if ($nonDemo.Count -ge 3) {
        # Dùng Random seed 42 chọn 3 user
        $shuffled = $nonDemo | Sort-Object { $Script:Rng.Next() }
        $privateUsernames = ($shuffled | Select-Object -First 3 | ForEach-Object { $_.Username })
    }

    foreach ($u in $Users) {
        # 1. Upload Avatar
        $avatarPath = Join-Path $AvatarDir "avatar_1.jpg"
        if ($u.Username -eq "demo") {
            $avatarPath = Join-Path $AvatarDir "avatar_1.jpg"
        } else {
            $idx = [int]($u.Username -replace '\D')
            if ($idx -lt 1) { $idx = 1 }
            if ($idx -gt 20) { $idx = 20 }
            $candidate = Join-Path $AvatarDir "avatar_$idx.jpg"
            if (Test-Path $candidate) { $avatarPath = $candidate }
        }

        $avatarUrl = $null
        if (Test-Path $avatarPath) {
            try {
                $mediaRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $avatarPath -Token $u.AccessToken
                $avatarUrl = $mediaRes.url
                $Summary.MediaUploaded++
            } catch {
                Write-WarnMsg "Upload avatar cho $($u.Username) thất bại: $($_.Exception.Message)"
            }
        }

        # 2. Thông tin Profile
        $profData = if ($u.Username -eq "demo") { $DemoProfile } else {
            $idx = [int]($u.Username -replace '\D') - 1
            if ($idx -ge 0 -and $idx -lt $VietnameseProfiles.Count) { $VietnameseProfiles[$idx] } else { $VietnameseProfiles[0] }
        }

        try {
            $profileBody = @{
                displayName = $profData.Name
                bio         = $profData.Bio
                website     = $profData.Web
                pronouns    = $profData.Pronouns
                category    = $profData.Cat
            }
            if ($avatarUrl) {
                $profileBody["avatarUrl"] = $avatarUrl
            }
            $null = Invoke-SnapgramApi -Url $Endpoints.MyProfile -Method "PATCH" -Body $profileBody -Token $u.AccessToken
            $Summary.ProfilesUpdated++
        } catch {
            Write-WarnMsg "Cập nhật profile $($u.Username) lỗi: $($_.Exception.Message)"
        }

        # 3. Đặt Private nếu nằm trong danh sách 3 user
        if ($privateUsernames -contains $u.Username) {
            try {
                $privBody = @{ privateAccount = $true }
                $null = Invoke-SnapgramApi -Url $Endpoints.UpdatePrivacy -Method "PUT" -Body $privBody -Token $u.AccessToken
                $u.IsPrivate = $true
                $Summary.PrivateUsers++
                Write-Host "  -> Đã bật Private cho tài khoản: $($u.Username)" -ForegroundColor Magenta
            } catch {
                Write-WarnMsg "Bật private cho $($u.Username) lỗi: $($_.Exception.Message)"
            }
        }

        # 4. Tạo Note trạng thái
        try {
            $noteText = $UserNotes[$Script:Rng.Next(0, $UserNotes.Count)]
            $noteBody = @{ content = $noteText }
            $null = Invoke-SnapgramApi -Url $Endpoints.Note -Method "POST" -Body $noteBody -Token $u.AccessToken
            $Summary.NotesCreated++
        } catch {
            # Bỏ qua nếu lỗi note
        }
    }

    Save-State -StateObj $StateObj
    Write-Success "Cập nhật hoàn tất: $($Summary.ProfilesUpdated) profiles, $($Summary.PrivateUsers) private accounts, $($Summary.NotesCreated) notes."
}

# ─────────────────────────────────────────────────────────────────────────────
# 9. BƯỚC 5: FOLLOW, FOLLOW REQUESTS & DUYỆT REQUEST
# ─────────────────────────────────────────────────────────────────────────────

function Step-CreateFollowRelationships {
    param($Users, $StateObj)
    Write-Step "Bước 5: Thiết lập quan hệ Follow và duyệt yêu cầu Follow Request"

    $userMap = @{}
    foreach ($u in $Users) { $userMap[$u.UserId] = $u }

    $pendingRequestsPerUser = @{} # TargetUserId -> List of FollowIds

    # Mỗi user follow 5-10 người khác
    foreach ($u in $Users) {
        $otherUsers = $Users | Where-Object { $_.UserId -ne $u.UserId }
        $followCount = $Script:Rng.Next(5, [Math]::Min(11, $otherUsers.Count + 1))
        $targets = $otherUsers | Sort-Object { $Script:Rng.Next() } | Select-Object -First $followCount

        foreach ($target in $targets) {
            try {
                $fUrl = $Endpoints.FollowUser -replace '\{userId\}', $target.UserId
                $followRes = Invoke-SnapgramApi -Url $fUrl -Method "POST" -Token $u.AccessToken
                $Summary.FollowsCreated++

                # Nếu target là Private -> trạng thái PENDING
                if ($followRes.status -eq "PENDING" -or $target.IsPrivate) {
                    $followId = $followRes.id
                    if ($followId) {
                        if (-not $pendingRequestsPerUser.ContainsKey($target.UserId)) {
                            $pendingRequestsPerUser[$target.UserId] = @()
                        }
                        $pendingRequestsPerUser[$target.UserId] += $followId
                    }
                }
            } catch {
                # Trùng lặp follow hoặc lỗi nhẹ bỏ qua
            }
        }
    }

    # Duyệt ~70% các follow request cho các user private
    Write-Host "  -> Đang duyệt ~70% các follow request của tài khoản Private..." -ForegroundColor Cyan
    foreach ($targetUserId in $pendingRequestsPerUser.Keys) {
        $targetUser = $userMap[$targetUserId]
        if (-not $targetUser) { continue }

        $reqList = $pendingRequestsPerUser[$targetUserId]
        foreach ($followId in $reqList) {
            # ~70% chấp nhận
            if ($Script:Rng.NextDouble() -le 0.70) {
                try {
                    $acceptUrl = $Endpoints.AcceptFollow -replace '\{followId\}', $followId
                    $null = Invoke-SnapgramApi -Url $acceptUrl -Method "POST" -Token $targetUser.AccessToken
                    $Summary.FollowsAccepted++
                } catch {}
            }
        }
    }

    Write-Success "Follow hoàn tất: Đã tạo $($Summary.FollowsCreated) follow, duyệt thành công $($Summary.FollowsAccepted) request."
}

# ─────────────────────────────────────────────────────────────────────────────
# 10. BƯỚC 6: SEED 60 POST THEO PHÂN BỐ CỐ ĐỊNH
# ─────────────────────────────────────────────────────────────────────────────

function Step-SeedPosts {
    param($Users, $StateObj)
    Write-Step "Bước 6: Seed 60 bài viết đa dạng theo phân bố cố định (Random Seed 42)"

    if ($StateObj.Posts.Count -ge $PostCount -and (-not $Force)) {
        Write-Host "  [SKIP] Đã seed $($StateObj.Posts.Count) bài viết trong state.json. Dùng -Force nếu muốn seed thêm." -ForegroundColor Gray
        return $StateObj.Posts
    }

    # Phân bố 60 bài viết:
    # - 1..25:  25 bài 1 ảnh
    # - 26..40: 15 bài carousel 2-5 ảnh
    # - 41..52: 12 bài 1 video
    # - 53..57: 5 bài carousel mixed ảnh + video
    # - 58..60: 3 bài chỉ text
    # Visibility: ~85% PUBLIC, ~15% FOLLOWERS

    $postsCreated = @()
    $successCount = 0
    $errorCount   = 0

    for ($pIdx = 1; $pIdx -le $PostCount; $pIdx++) {
        # Chọn tác giả luân phiên (demo hoặc user1..user20)
        $author = $Users[($pIdx - 1) % $Users.Count]
        $caption = $PostCaptions[$Script:Rng.Next(0, $PostCaptions.Count)]

        # Visibility: 1..51 -> PUBLIC (~85%), 52..60 -> FOLLOWERS (~15%)
        $visibility = if ($pIdx -le 51) { "PUBLIC" } else { "FOLLOWERS" }

        $mediaList = @()
        $contentType = $null

        if ($pIdx -le 25) {
            # 25 BÀI 1 ẢNH
            $imgIdx = (($pIdx - 1) % 60) + 1
            $imgPath = Join-Path $PostImgDir "post_$imgIdx.jpg"
            if (Test-Path $imgPath) {
                $uploadRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgPath -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{
                    mediaId      = $uploadRes.id
                    mediaUrl     = $uploadRes.url
                    thumbnailUrl = $uploadRes.thumbnailUrl
                    mediaType    = "IMAGE"
                    sortOrder    = 0
                    width        = 1080
                    height       = 1080
                }
            }
            $contentType = "IMAGE"

        } elseif ($pIdx -le 40) {
            # 15 BÀI CAROUSEL 2-5 ẢNH
            $numItems = $Script:Rng.Next(2, 6) # 2..5 ảnh
            for ($s = 0; $s -lt $numItems; $s++) {
                $imgNum = (($pIdx * 3 + $s) % 60) + 1
                $imgPath = Join-Path $PostImgDir "post_$imgNum.jpg"
                if (Test-Path $imgPath) {
                    $uploadRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgPath -Token $author.AccessToken
                    $Summary.MediaUploaded++
                    $mediaList += @{
                        mediaId      = $uploadRes.id
                        mediaUrl     = $uploadRes.url
                        thumbnailUrl = $uploadRes.thumbnailUrl
                        mediaType    = "IMAGE"
                        sortOrder    = $s
                        width        = 1080
                        height       = 1080
                    }
                }
            }
            $contentType = "CAROUSEL"

        } elseif ($pIdx -le 52) {
            # 12 BÀI 1 VIDEO
            $vidIdx = (($pIdx - 41) % 12) + 1
            $vidPath = Join-Path $VideoDir "video_$vidIdx.mp4"
            if ($Script:HasFfmpeg -and (Test-Path $vidPath)) {
                $uploadRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $vidPath -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{
                    mediaId      = $uploadRes.id
                    mediaUrl     = $uploadRes.url
                    thumbnailUrl = $uploadRes.thumbnailUrl
                    mediaType    = "VIDEO"
                    sortOrder    = 0
                    durationSec  = 8
                    width        = 1080
                    height       = 1920
                }
                $contentType = "VIDEO"
            } else {
                # Fallback sang ảnh nếu không có video
                $imgFallback = Join-Path $PostImgDir "post_$vidIdx.jpg"
                $uploadRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgFallback -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{
                    mediaId   = $uploadRes.id
                    mediaUrl  = $uploadRes.url
                    mediaType = "IMAGE"
                    sortOrder = 0
                }
                $contentType = "IMAGE"
            }

        } elseif ($pIdx -le 57) {
            # 5 BÀI CAROUSEL MIXED (TRỘN ẢNH + VIDEO)
            # 1 ảnh + 1 video + 1 ảnh
            $imgA = Join-Path $PostImgDir "post_$(($pIdx * 2) % 60 + 1).jpg"
            $imgB = Join-Path $PostImgDir "post_$(($pIdx * 2 + 1) % 60 + 1).jpg"
            $vidA = Join-Path $VideoDir "video_$(($pIdx % 12) + 1).mp4"

            # Item 0: IMAGE
            if (Test-Path $imgA) {
                $resA = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgA -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{ mediaId = $resA.id; mediaUrl = $resA.url; mediaType = "IMAGE"; sortOrder = 0 }
            }
            # Item 1: VIDEO (hoặc fallback ảnh nếu không có video)
            if ($Script:HasFfmpeg -and (Test-Path $vidA)) {
                $resV = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $vidA -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{ mediaId = $resV.id; mediaUrl = $resV.url; mediaType = "VIDEO"; sortOrder = 1; durationSec = 8 }
            } elseif (Test-Path $imgB) {
                $resB = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgB -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{ mediaId = $resB.id; mediaUrl = $resB.url; mediaType = "IMAGE"; sortOrder = 1 }
            }
            # Item 2: IMAGE
            if (Test-Path $imgB) {
                $resB = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgB -Token $author.AccessToken
                $Summary.MediaUploaded++
                $mediaList += @{ mediaId = $resB.id; mediaUrl = $resB.url; mediaType = "IMAGE"; sortOrder = 2 }
            }
            $contentType = "CAROUSEL"

        } else {
            # 3 BÀI CHỈ TEXT (58..60)
            $mediaList = @()
            $contentType = "TEXT"
        }

        # Tạo bài đăng
        try {
            $postReq = @{
                caption     = $caption
                visibility  = $visibility
                contentType = $contentType
            }
            if ($mediaList.Count -gt 0) {
                $postReq["mediaList"] = $mediaList
            }

            $postRes = Invoke-SnapgramApi -Url $Endpoints.Posts -Method "POST" -Body $postReq -Token $author.AccessToken
            $postsCreated += [PSCustomObject]@{
                PostId      = $postRes.id
                AuthorId    = $author.UserId
                ContentType = $postRes.contentType
                IsVideo     = ($contentType -eq "VIDEO" -or ($mediaList | Where-Object { $_.mediaType -eq "VIDEO" }))
            }
            $Summary.PostsCreated++
            $successCount++
            if ($pIdx % 10 -eq 0) {
                Write-Host "  -> Đã tạo $pIdx/$PostCount bài viết..." -ForegroundColor Green
            }
        } catch {
            $errorCount++
            Write-WarnMsg "Tạo bài $pIdx thất bại: $($_.Exception.Message)"
        }

        Assert-PhaseSuccess -PhaseName "Create Posts" -SuccessCount $successCount -ErrorCount $errorCount -LastEndpoint $Endpoints.Posts
    }

    $StateObj.Posts = $postsCreated
    Save-State -StateObj $StateObj
    Write-Success "Seed thành công $($postsCreated.Count)/$PostCount bài viết."
    return $postsCreated
}

# ─────────────────────────────────────────────────────────────────────────────
# 11. BƯỚC 7: TẠO TƯƠNG TÁC (LIKE, COMMENT, REPLY, SAVE, VIEW, SKIP)
# ─────────────────────────────────────────────────────────────────────────────

function Step-SeedInteractions {
    param($Posts, $Users, $StateObj)
    Write-Step "Bước 7: Tạo tương tác phong phú (Like, Comment, Reply, Like Comment, Save, Video View/Skip)"

    $userMap = @{}
    foreach ($u in $Users) { $userMap[$u.UserId] = $u }

    foreach ($post in $Posts) {
        $postId = $post.PostId

        # 1. LIKE POST (0..15 like)
        $likeCount = $Script:Rng.Next(0, 16)
        $likers = $Users | Sort-Object { $Script:Rng.Next() } | Select-Object -First $likeCount
        foreach ($liker in $likers) {
            try {
                $interactUrl = $Endpoints.PostInteract -replace '\{postId\}', $postId
                $likeBody = @{ type = "LIKE" }
                $null = Invoke-SnapgramApi -Url $interactUrl -Method "POST" -Body $likeBody -Token $liker.AccessToken
                $Summary.LikesCreated++
            } catch {}
        }

        # 2. COMMENT & REPLY (0..5 comment)
        $commentCount = $Script:Rng.Next(0, 6)
        $commenters = $Users | Sort-Object { $Script:Rng.Next() } | Select-Object -First $commentCount
        $createdCommentIds = @()

        foreach ($commenter in $commenters) {
            try {
                $cUrl = $Endpoints.PostComments -replace '\{postId\}', $postId
                $cText = $CommentBank[$Script:Rng.Next(0, $CommentBank.Count)]
                $cBody = @{ content = $cText }
                $cRes = Invoke-SnapgramApi -Url $cUrl -Method "POST" -Body $cBody -Token $commenter.AccessToken
                $Summary.CommentsCreated++
                if ($cRes.id) {
                    $createdCommentIds += $cRes.id
                }
            } catch {}
        }

        # Reply: ~35% có reply cho comment đầu tiên
        if ($createdCommentIds.Count -gt 0 -and ($Script:Rng.NextDouble() -le 0.35)) {
            $rootCommentId = $createdCommentIds[0]
            $replier = $Users[$Script:Rng.Next(0, $Users.Count)]
            try {
                $replyUrl = $Endpoints.PostComments -replace '\{postId\}', $postId
                $replyText = $ReplyBank[$Script:Rng.Next(0, $ReplyBank.Count)]
                $replyBody = @{
                    content         = $replyText
                    parentCommentId = $rootCommentId
                }
                $null = Invoke-SnapgramApi -Url $replyUrl -Method "POST" -Body $replyBody -Token $replier.AccessToken
                $Summary.RepliesCreated++
            } catch {}
        }

        # Like Comment: ~40% comment được like
        foreach ($cId in $createdCommentIds) {
            if ($Script:Rng.NextDouble() -le 0.40) {
                $liker = $Users[$Script:Rng.Next(0, $Users.Count)]
                try {
                    $cLikeUrl = $Endpoints.CommentLike -replace '\{postId\}', $postId -replace '\{commentId\}', $cId
                    $null = Invoke-SnapgramApi -Url $cLikeUrl -Method "POST" -Token $liker.AccessToken
                    $Summary.CommentLikes++
                } catch {}
            }
        }

        # 3. SAVE POST (~15% bài được save)
        if ($Script:Rng.NextDouble() -le 0.20) {
            $saver = $Users[$Script:Rng.Next(0, $Users.Count)]
            try {
                $sUrl = $Endpoints.SavedPosts -replace '\{postId\}', $postId
                $null = Invoke-SnapgramApi -Url $sUrl -Method "POST" -Token $saver.AccessToken
                $Summary.SavesCreated++
            } catch {}
        }

        # 4. VIDEO INTERACTIONS: VIEW (watchTimeRatio 0.1..1.0) & SKIP
        if ($post.IsVideo) {
            # Thêm vài lượt VIEW với watchTimeRatio
            $viewers = $Users | Sort-Object { $Script:Rng.Next() } | Select-Object -First $Script:Rng.Next(3, 8)
            foreach ($viewer in $viewers) {
                try {
                    $interactUrl = $Endpoints.PostInteract -replace '\{postId\}', $postId
                    $ratio = [Math]::Round(($Script:Rng.NextDouble() * 0.9 + 0.1), 2) # 0.10 .. 1.00
                    $viewBody = @{
                        type           = "VIEW"
                        watchTimeRatio = $ratio
                    }
                    $null = Invoke-SnapgramApi -Url $interactUrl -Method "POST" -Body $viewBody -Token $viewer.AccessToken
                    $Summary.ViewsCreated++
                } catch {}
            }

            # Thêm 1..2 SKIP
            if ($Script:Rng.NextDouble() -le 0.50) {
                $skipper = $Users[$Script:Rng.Next(0, $Users.Count)]
                try {
                    $interactUrl = $Endpoints.PostInteract -replace '\{postId\}', $postId
                    $skipBody = @{ type = "SKIP" }
                    $null = Invoke-SnapgramApi -Url $interactUrl -Method "POST" -Body $skipBody -Token $skipper.AccessToken
                    $Summary.SkipsCreated++
                } catch {}
            }
        }
    }

    Write-Success "Tương tác hoàn tất: $($Summary.LikesCreated) likes, $($Summary.CommentsCreated) comments, $($Summary.RepliesCreated) replies, $($Summary.CommentLikes) comment likes, $($Summary.SavesCreated) saves, $($Summary.ViewsCreated) views, $($Summary.SkipsCreated) skips."
}

# ─────────────────────────────────────────────────────────────────────────────
# 12. BƯỚC 8: SEED 15 STORIES
# ─────────────────────────────────────────────────────────────────────────────

function Step-SeedStories {
    param($Users, $StateObj)
    Write-Step "Bước 8: Seed 15 Stories (ảnh, video, đủ 3 mức visibility)"

    if ($StateObj.Stories.Count -ge $StoryCount -and (-not $Force)) {
        Write-Host "  [SKIP] Đã seed $($StateObj.Stories.Count) story trong state.json. Dùng -Force để seed thêm." -ForegroundColor Gray
        return
    }

    $storiesCreated = @()
    # 15 Stories: ~10 ảnh (1..10), ~5 video (11..15)
    # Visibility luân phiên: PUBLIC, FOLLOWERS, CLOSE_FRIENDS

    for ($sIdx = 1; $sIdx -le $StoryCount; $sIdx++) {
        $author = $Users[($sIdx - 1) % $Users.Count]
        $visibility = switch ($sIdx % 3) {
            0 { "PUBLIC" }
            1 { "FOLLOWERS" }
            2 { "CLOSE_FRIENDS" }
        }

        $mediaUrl = $null
        $mediaId  = $null
        $mediaType = "IMAGE"

        if ($sIdx -le 10 -or (-not $Script:HasFfmpeg)) {
            # Story Ảnh
            $imgPath = Join-Path $StoryImgDir "story_$sIdx.jpg"
            if (Test-Path $imgPath) {
                try {
                    $upRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $imgPath -Token $author.AccessToken
                    $mediaUrl = $upRes.url
                    $mediaId  = $upRes.id
                    $Summary.MediaUploaded++
                } catch {}
            }
            $mediaType = "IMAGE"
        } else {
            # Story Video
            $vidIdx = (($sIdx - 11) % 12) + 1
            $vidPath = Join-Path $VideoDir "video_$vidIdx.mp4"
            if (Test-Path $vidPath) {
                try {
                    $upRes = Upload-MediaFile -Url $Endpoints.UploadMedia -FilePath $vidPath -Token $author.AccessToken
                    $mediaUrl = $upRes.url
                    $mediaId  = $upRes.id
                    $Summary.MediaUploaded++
                } catch {}
            }
            $mediaType = "VIDEO"
        }

        if ($mediaUrl) {
            try {
                $storyBody = @{
                    mediaUrl   = $mediaUrl
                    mediaId    = $mediaId
                    mediaType  = $mediaType
                    caption    = "Story khoảnh khắc số $sIdx ✨"
                    visibility = $visibility
                }
                $storyRes = Invoke-SnapgramApi -Url $Endpoints.Stories -Method "POST" -Body $storyBody -Token $author.AccessToken
                $storiesCreated += $storyRes.id
                $Summary.StoriesCreated++
            } catch {
                Write-WarnMsg "Tạo story $sIdx thất bại: $($_.Exception.Message)"
            }
        }
    }

    $StateObj.Stories = $storiesCreated
    Save-State -StateObj $StateObj
    Write-Success "Seed hoàn tất $($Summary.StoriesCreated)/$StoryCount stories."
}

# ─────────────────────────────────────────────────────────────────────────────
# 13. BƯỚC 9: KÍCH HOẠT SPRING BATCH JOBS (RECOMMENDER)
# ─────────────────────────────────────────────────────────────────────────────

function Step-TriggerSpringBatchJobs {
    param($Users)
    Write-Step "Bước 9: Kích hoạt thủ công Spring Batch Jobs (Recommender Service)"

    if ($SkipBatch) {
        Write-Host "  [SKIP] Tham số -SkipBatch được chỉ định. Bỏ qua chạy Batch." -ForegroundColor Gray
        $Summary.SkippedItems += "Kích hoạt Batch Recommender (-SkipBatch được kích hoạt)"
        return
    }

    $demoUser = $Users | Where-Object { $_.Username -eq "demo" } | Select-Object -First 1
    if (-not $demoUser) {
        Write-WarnMsg "Không tìm thấy tài khoản 'demo' để kích hoạt Batch. Bỏ qua."
        $Summary.SkippedItems += "Kích hoạt Batch (thiếu tài khoản admin demo)"
        return
    }

    $jobNames = @("trendingPostsJob", "userRecommendationJob")
    foreach ($jName in $jobNames) {
        Write-Host "  -> Đang kích hoạt job: $jName..." -ForegroundColor Cyan
        try {
            $runUrl = $Endpoints.RunBatchJob -replace '\{jobName\}', $jName
            $runRes = Invoke-SnapgramApi -Url $runUrl -Method "POST" -Token $demoUser.AccessToken
            $executionId = $runRes.jobExecutionId
            Write-Host "     Job đã chạy với ExecutionId=$executionId. Đang chờ hoàn tất (COMPLETED)..." -ForegroundColor Gray

            # Poll tối đa 30s
            $completed = $false
            for ($wait = 1; $wait -le 15; $wait++) {
                Start-Sleep -Seconds 2
                try {
                    $pollUrl = $Endpoints.GetBatchExecution -replace '\{id\}', $executionId
                    $statusRes = Invoke-SnapgramApi -Url $pollUrl -Method "GET" -Token $demoUser.AccessToken
                    if ($statusRes.status -eq "COMPLETED") {
                        Write-Success "Job $jName hoàn thành xuất sắc! (Thời gian: $($statusRes.durationMs)ms)"
                        $completed = $true
                        $Summary.BatchJobsRun++
                        break
                    } elseif ($statusRes.status -eq "FAILED" -or $statusRes.status -eq "STOPPED") {
                        Write-WarnMsg "Job $jName kết thúc với trạng thái: $($statusRes.status)"
                        break
                    }
                } catch {
                    # Lỗi poll tạm thời
                }
            }
            if (-not $completed) {
                Write-WarnMsg "Hết thời gian chờ job $jName (hoặc job đang chạy nền)."
            }
        } catch {
            Write-WarnMsg "Kích hoạt job $jName thất bại: $($_.Exception.Message)"
            $Summary.SkippedItems += "Job $($jName): $($_.Exception.Message)"
        }
    }
}

# ─────────────────────────────────────────────────────────────────────────────
# 14. CHẾ ĐỘ PROBE ONLY (-ProbeOnly)
# ─────────────────────────────────────────────────────────────────────────────

function Run-ProbeOnly {
    Write-Step "CHẾ ĐỘ KIỂM TRA (-ProbeOnly): Kiểm tra Gateway, Auth & Token Claims"

    Write-Host "1. Kiểm tra kết nối tới Gateway: $BaseUrl..."
    try {
        # Test qua login endpoint với body rỗng xem server có phản hồi 400 không
        $testRes = Invoke-WebRequest -Uri $Endpoints.Login -Method POST -Body "{}" -ContentType "application/json" -TimeoutSec 5 -ErrorAction SilentlyContinue
    } catch {
        # Mong đợi 400 Bad Request
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 400) {
            Write-Success "API Gateway tiếp nhận request bình thường (HTTP 400 với body rỗng)."
        } else {
            Write-WarnMsg "Kết nối tới Gateway có thể bị gián đoạn: $($_.Exception.Message)"
        }
    }

    Write-Host "`n2. Đăng ký & Đăng nhập tài khoản test probe..."
    $probeUser = "probe_user_$(Get-Random -Minimum 1000 -Maximum 9999)"
    $probeEmail = "$probeUser@demo.local"

    try {
        $regBody = @{
            username    = $probeUser
            email       = $probeEmail
            password    = $DemoPassword
            displayName = "Probe Tester"
        }
        $authRes = Invoke-SnapgramApi -Url $Endpoints.Register -Method "POST" -Body $regBody
        Write-Success "Đăng ký thành công probe user: $probeUser"
        
        $token = $authRes.accessToken
        $payload = Get-JwtPayload -Token $token
        if ($payload) {
            Write-Success "Giải mã JWT payload thành công:"
            Write-Host "   - sub (userId) : $($payload.sub)" -ForegroundColor Gray
            Write-Host "   - username     : $($payload.username)" -ForegroundColor Gray
            Write-Host "   - roles        : $($payload.roles -join ',')" -ForegroundColor Gray
        } else {
            Write-WarnMsg "Không thể giải mã payload JWT từ token trả về."
        }
    } catch {
        Write-ErrMsg "Probe thất bại: $($_.Exception.Message)"
        exit 1
    }

    Write-Host "`n====> KẾT QUẢ PROBE: TOÀN BỘ KẾT NỐI VÀ CHỨC NĂNG CƠ BẢN HOẠT ĐỘNG TỐT!" -ForegroundColor Green
    exit 0
}

# ─────────────────────────────────────────────────────────────────────────────
# 15. MAIN EXECUTION FLOW
# ─────────────────────────────────────────────────────────────────────────────

function Main {
    Write-Host @"
========================================================================
       SNAPGRAM SERVER - SEED DEMO DATA AUTOMATION SCRIPT
========================================================================
  Gateway URL:      $BaseUrl
  Mật khẩu Demo:    $DemoPassword
  Số lượng User:    $UserCount (+ demo)
  Số lượng Post:    $PostCount
  Số lượng Story:   $StoryCount
  Môi trường:       $($PSVersionTable.PSEdition) $($PSVersionTable.PSVersion)
========================================================================
"@ -ForegroundColor Yellow

    if ($ProbeOnly) {
        Run-ProbeOnly
        return
    }

    # Tải state hiện tại
    $state = Load-State

    # Bước 0: Chuẩn bị thư mục và assets mẫu
    Prepare-Assets

    # Bước 1: Đăng ký & Đăng nhập
    $users = Step-CreateAndLoginUsers -StateObj $state

    # Bước 2: Thực thi SQL exceptions
    Step-ExecuteSqlExceptions -StateObj $state

    # Bước 3: Chờ 3s cho Kafka sync projection
    Write-Step "Bước 3: Chờ 3 giây để Kafka đồng bộ projection view sang downstream services..."
    Start-Sleep -Seconds 3
    Write-Success "Đã đồng bộ xong."

    # Bước 4: Profile, Avatar, Private, Notes
    Step-UpdateProfilesAndAvatars -Users $users -StateObj $state

    # Bước 5: Follow & Duyệt request
    Step-CreateFollowRelationships -Users $users -StateObj $state

    # Bước 6: Seed 60 Posts
    $posts = Step-SeedPosts -Users $users -StateObj $state

    # Bước 7: Tương tác (Like, Comment, Reply, Save, Video view/skip)
    Step-SeedInteractions -Posts $posts -Users $users -StateObj $state

    # Bước 8: Seed 15 Stories
    Step-SeedStories -Users $users -StateObj $state

    # Bước 9: Kích hoạt Spring Batch
    Step-TriggerSpringBatchJobs -Users $users

    # Bảng tổng kết
    Write-Host "`n"
    Write-Host "========================================================================" -ForegroundColor Yellow
    Write-Host "                BẢNG TỔNG KẾT DỮ LIỆU SEED DEMO" -ForegroundColor Yellow
    Write-Host "========================================================================" -ForegroundColor Yellow
    Write-Host ("  {0,-30} : {1}" -f "Người dùng mới tạo", $Summary.UsersCreated)
    Write-Host ("  {0,-30} : {1}" -f "Người dùng đã có sẵn", $Summary.UsersExisting)
    Write-Host ("  {0,-30} : {1}" -f "Profile đã cập nhật", $Summary.ProfilesUpdated)
    Write-Host ("  {0,-30} : {1}" -f "Tài khoản Private", $Summary.PrivateUsers)
    Write-Host ("  {0,-30} : {1}" -f "Ghi chú (Notes)", $Summary.NotesCreated)
    Write-Host ("  {0,-30} : {1}" -f "Quan hệ Follow đã tạo", $Summary.FollowsCreated)
    Write-Host ("  {0,-30} : {1}" -f "Follow Request đã duyệt", $Summary.FollowsAccepted)
    Write-Host ("  {0,-30} : {1}" -f "Media files đã tải lên", $Summary.MediaUploaded)
    Write-Host ("  {0,-30} : {1}" -f "Bài viết đã đăng (Posts)", $Summary.PostsCreated)
    Write-Host ("  {0,-30} : {1}" -f "Lượt thích bài viết (Likes)", $Summary.LikesCreated)
    Write-Host ("  {0,-30} : {1}" -f "Bình luận (Comments)", $Summary.CommentsCreated)
    Write-Host ("  {0,-30} : {1}" -f "Câu trả lời (Replies)", $Summary.RepliesCreated)
    Write-Host ("  {0,-30} : {1}" -f "Lượt thích bình luận", $Summary.CommentLikes)
    Write-Host ("  {0,-30} : {1}" -f "Bài viết đã lưu (Saves)", $Summary.SavesCreated)
    Write-Host ("  {0,-30} : {1}" -f "Lượt xem video (Views)", $Summary.ViewsCreated)
    Write-Host ("  {0,-30} : {1}" -f "Lượt bỏ qua video (Skips)", $Summary.SkipsCreated)
    Write-Host ("  {0,-30} : {1}" -f "Stories đã tạo", $Summary.StoriesCreated)
    Write-Host ("  {0,-30} : {1}" -f "Spring Batch jobs đã chạy", $Summary.BatchJobsRun)

    if ($Summary.SkippedItems.Count -gt 0) {
        Write-Host "`n  Các mục bị bỏ qua / ghi chú:" -ForegroundColor Yellow
        foreach ($skip in $Summary.SkippedItems) {
            Write-Host "   - $skip" -ForegroundColor Gray
        }
    }

    Write-Host "`n------------------------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "  LƯU Ý QUAN TRỌNG VỀ DEMO:" -ForegroundColor Yellow
    Write-Host "  1. Story và Note trong Instagram/Snapgram sẽ tự động hết hạn sau 24 GIỜ." -ForegroundColor Yellow
    Write-Host "     -> Khuyến nghị: Chạy lại script ngay trước buổi thuyết trình/demo." -ForegroundColor Yellow
    Write-Host "  2. Đăng nhập tài khoản demo:" -ForegroundColor Cyan
    Write-Host "     - Email:    demo@demo.local" -ForegroundColor Green
    Write-Host "     - Password: $DemoPassword" -ForegroundColor Green
    Write-Host "     - Quyền:    ADMIN (kèm Verified Blue Tick)" -ForegroundColor Green
    Write-Host "========================================================================`n" -ForegroundColor Yellow
}

# Run script
Main
