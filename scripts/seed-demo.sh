#!/usr/bin/env bash
# ==============================================================================
# Script Seed Data Demo cho mạng xã hội Snapgram (Bản Bash / Git Bash / Linux / macOS)
# ==============================================================================

set -o pipefail

# 1. KIỂM TRA CÔNG CỤ BẮT BUỘC (CURL & JQ)
for cmd in curl jq; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "[FAIL] Lệnh '$cmd' chưa được cài đặt trong môi trường!" >&2
    echo "  - Trên Ubuntu/Debian: sudo apt-get update && sudo apt-get install -y $cmd" >&2
    echo "  - Trên macOS: brew install $cmd" >&2
    echo "  - Trên Windows Git Bash: tải jq.exe từ https://jqlang.github.io/jq/ và đặt vào PATH" >&2
    exit 1
  fi
done

# 2. CẤU HÌNH THAM SỐ MẶC ĐỊNH
BASE_URL="http://localhost:8080"
DEMO_PASSWORD="Demo@12345"
USER_COUNT=20
POST_COUNT=60
STORY_COUNT=15
PROBE_ONLY=false
FORCE=false
DB_CONTAINER=""
SKIP_BATCH=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)       BASE_URL="$2"; shift 2 ;;
    --demo-password)  DEMO_PASSWORD="$2"; shift 2 ;;
    --user-count)     USER_COUNT="$2"; shift 2 ;;
    --post-count)     POST_COUNT="$2"; shift 2 ;;
    --story-count)    STORY_COUNT="$2"; shift 2 ;;
    --probe-only)     PROBE_ONLY=true; shift ;;
    --force)          FORCE=true; shift ;;
    --db-container)   DB_CONTAINER="$2"; shift 2 ;;
    --skip-batch)     SKIP_BATCH=true; shift ;;
    -h|--help)
      echo "Cách dùng: $0 [OPTIONS]"
      echo "  --base-url URL         Địa chỉ Gateway (mặc định: http://localhost:8080)"
      echo "  --demo-password PASS   Mật khẩu demo (mặc định: Demo@12345)"
      echo "  --user-count N         Số lượng user phụ (mặc định: 20)"
      echo "  --post-count N         Số lượng post (mặc định: 60)"
      echo "  --story-count N        Số lượng story (mặc định: 15)"
      echo "  --probe-only           Chỉ kiểm tra kết nối & đăng ký probe, không seed"
      echo "  --force                Ép buộc seed lại bỏ qua state.json"
      echo "  --db-container NAME    Tên container PostgreSQL auth"
      echo "  --skip-batch           Bỏ qua kích hoạt Spring Batch"
      exit 0
      ;;
    *)
      echo "Tham số không hợp lệ: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_DIR="$SCRIPT_DIR/seed"
AVATAR_DIR="$SEED_DIR/avatars"
POST_IMG_DIR="$SEED_DIR/posts"
STORY_IMG_DIR="$SEED_DIR/stories"
VIDEO_DIR="$SEED_DIR/videos"
STATE_FILE="$SEED_DIR/state.json"

mkdir -p "$SEED_DIR" "$AVATAR_DIR" "$POST_IMG_DIR" "$STORY_IMG_DIR" "$VIDEO_DIR"

# Linear Congruential Generator (LCG) cố định seed 42 để đồng bộ kết quả
RNG_SEED=42
next_random() {
  local max="$1"
  RNG_SEED=$(( (RNG_SEED * 1103515245 + 12345) & 0x7fffffff ))
  if [ -n "$max" ] && [ "$max" -gt 0 ]; then
    echo $(( RNG_SEED % max ))
  else
    echo "$RNG_SEED"
  fi
}

# Bộ đếm tổng kết
COUNT_USERS_CREATED=0
COUNT_USERS_EXISTING=0
COUNT_PROFILES_UPDATED=0
COUNT_PRIVATE_USERS=0
COUNT_NOTES_CREATED=0
COUNT_FOLLOWS_CREATED=0
COUNT_FOLLOWS_ACCEPTED=0
COUNT_MEDIA_UPLOADED=0
COUNT_POSTS_CREATED=0
COUNT_LIKES_CREATED=0
COUNT_COMMENTS_CREATED=0
COUNT_REPLIES_CREATED=0
COUNT_COMMENT_LIKES=0
COUNT_SAVES_CREATED=0
COUNT_VIEWS_CREATED=0
COUNT_SKIPS_CREATED=0
COUNT_STORIES_CREATED=0
COUNT_BATCH_RUN=0
SKIPPED_REASONS=()

# Helper giải mã JWT payload và trích xuất claim (Base64URL padding handling)
decode_jwt() {
  local token="$1"
  local claim="$2"
  local raw_payload
  raw_payload=$(echo "$token" | cut -d'.' -f2 2>/dev/null)
  if [ -z "$raw_payload" ]; then return 1; fi

  # Chuyển đổi Base64URL sang Base64 chuẩn
  local b64
  b64=$(echo "$raw_payload" | tr '_-' '/+')
  local rem=$((${#b64} % 4))
  if [ "$rem" -eq 2 ]; then b64="${b64}=="; elif [ "$rem" -eq 3 ]; then b64="${b64}="; fi

  local json
  json=$(echo "$b64" | base64 -d 2>/dev/null)
  if [ -n "$claim" ]; then
    echo "$json" | jq -r ".$claim // empty" 2>/dev/null
  else
    echo "$json"
  fi
}

# Helper gọi API REST qua Gateway có retry khi 429/5xx và in body lỗi
api_request() {
  local method="$1"
  local path="$2"
  local body="$3"
  local token="$4"
  local url="${BASE_URL}${path}"

  local max_retries=3
  local attempt=0

  while [ "$attempt" -lt "$max_retries" ]; do
    attempt=$((attempt + 1))
    local headers=(-H "Accept: application/json")
    if [ -n "$token" ]; then
      headers+=(-H "Authorization: Bearer $token")
    fi
    if [ -n "$body" ]; then
      headers+=(-H "Content-Type: application/json; charset=utf-8")
    fi

    local response
    local http_code
    if [ -n "$body" ] && [ "$method" != "GET" ] && [ "$method" != "DELETE" ]; then
      response=$(curl -s -S -X "$method" "${headers[@]}" -d "$body" -w "\n%{http_code}" "$url" 2>&1)
    else
      response=$(curl -s -S -X "$method" "${headers[@]}" -w "\n%{http_code}" "$url" 2>&1)
    fi

    http_code=$(echo "$response" | tail -n1)
    local body_content
    body_content=$(echo "$response" | sed '$d')

    if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
      echo "$body_content"
      return 0
    elif [ "$http_code" -eq 429 ] || { [ "$http_code" -ge 500 ] && [ "$http_code" -le 504 ]; }; then
      if [ "$attempt" -lt "$max_retries" ]; then
        local sleep_sec=$((attempt * 2))
        echo "  [WARN] API $method $url trả về HTTP $http_code. Đang thử lại lần $attempt/$max_retries sau ${sleep_sec}s..." >&2
        sleep "$sleep_sec"
        continue
      fi
    fi

    echo "  [FAIL] API $method $url thất bại (HTTP $http_code): $body_content" >&2
    echo "ERROR:$http_code:$body_content"
    return 1
  done
}

# Helper upload multipart file
upload_media() {
  local file_path="$1"
  local token="$2"
  local url="${BASE_URL}/api/media/upload"

  if [ ! -f "$file_path" ]; then
    echo "File không tồn tại: $file_path" >&2
    return 1
  fi

  local mime_type="application/octet-stream"
  case "$file_path" in
    *.jpg|*.jpeg) mime_type="image/jpeg" ;;
    *.png)        mime_type="image/png" ;;
    *.webp)       mime_type="image/webp" ;;
    *.mp4)        mime_type="video/mp4" ;;
  esac

  local res
  res=$(curl -s -S -X POST "$url" \
    -H "Authorization: Bearer $token" \
    -F "file=@$file_path;type=$mime_type" \
    -w "\n%{http_code}" 2>&1)

  local http_code
  http_code=$(echo "$res" | tail -n1)
  local body
  body=$(echo "$res" | sed '$d')

  if [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]; then
    echo "$body"
    return 0
  else
    echo "Upload thất bại (HTTP $http_code): $body" >&2
    return 1
  fi
}

# Mảng dữ liệu tiếng Việt
VIETNAMESE_NAMES=(
  "Nguyễn Minh Tuấn" "Trần Thị Mai" "Lê Hoàng Long" "Phạm Hải Đăng" "Vũ Phương Anh"
  "Đặng Quốc Bảo" "Bùi Thu Hà" "Đỗ Minh Khang" "Hồ Bích Ngọc" "Ngô Thành Nam"
  "Dương Thu Trang" "Lý Gia Hưng" "Võ Diệu Linh" "Phan Nhật Huy" "Hoàng Kim Oanh"
  "Trịnh Anh Tuấn" "Mai Phương Thảo" "Lâm Khánh Chi" "Đinh Đức Trọng" "Cao Thùy Dung"
)

VIETNAMESE_BIOS=(
  "Đam mê nhiếp ảnh và du lịch 📸 Chụp cuộc sống qua ống kính 🌿"
  "Food reviewer tại Hà Nội & Sài Gòn 🍜 Ăn sập mọi ngõ ngách"
  "Software Engineer & Tech Enthusiast 💻 Cà phê và những dòng code"
  "Fitness & Healthy Lifestyle 💪 Mỗi ngày một chút tiến bộ vượt bậc"
  "Digital Creator & Storyteller ✨ Kết nối những câu chuyện thú vị"
  "Yêu sách, trà chiều và những giai điệu acoustic bình yên ☕📖"
  "Kiến trúc sư trẻ | Thiết kế không gian sống tối giản & hiện đại 🏛️"
  "Khởi nghiệp công nghệ | Chia sẻ kiến thức và hành trình mỗi ngày 🚀"
  "Thời trang bền vững & Phong cách sống xanh 🌱 Less is more"
  "Nhạc sĩ độc lập | Những giai điệu mộc mạc từ trái tim 🎸"
)

CAPTIONS=(
  "Hoàng hôn chiều nay tại Hồ Tây đẹp đến nao lòng 🌅 #hanoi #sunset #peace"
  "Ghé thăm quán cà phê nhỏ ẩn mình trong ngõ phố cổ ☕ #coffeetime #chill"
  "Những chuyến đi luôn mang lại cho chúng ta góc nhìn mới mẻ 🌲🍃 #dalat #travel"
  "Hoàn thành xong dự án lớn sau chuỗi ngày nỗ lực không ngừng 💻🚀 #developer"
  "Một ngày cuối tuần bình yên bên trang sách yêu thích 📖🍵 #reading #slowliving"
  "Sài Gòn ngày nắng đẹp rạng rỡ. Dạo bước qua những con đường rợp bóng cây ☀️ #saigon"
  "Bát phở bò gia truyền nóng hổi sớm mai thơm nức mũi 🍜 #vietnamesefood #pho"
  "Chuyến trekking băng rừng nguyên sinh chạm tới đỉnh mây ⛰️🥾 #adventure"
  "Ánh sáng vàng chiều buông là món quà tuyệt nhất của thiên nhiên 📷✨ #goldenhour"
  "Cắm trại đêm bên bờ hồ vắng ngắm nhìn dải ngân hà 🔥⛺🌌 #camping #chill"
)

COMMENTS=(
  "Ảnh chụp góc này đỉnh quá bạn ơi! Tone màu rất ưng mắt 👏"
  "Địa điểm này ở đâu thế ạ, nhìn thơ mộng quá!"
  "Bình yên thực sự, nhìn bức ảnh mà thấy lòng nhẹ nhõm hẳn ❤️"
  "Nội dung chia sẻ rất hữu ích, cảm ơn bạn nhiều nhé!"
  "Đẹp xuất sắc luôn! Đúng chất Snapgram nghệ thuật ✨"
)

NOTES=(
  "Đang nghe nhạc lofi chill 🎧"
  "Cần một ly cà phê đậm đặc ngay lúc này ☕"
  "Hôm nay trời đẹp quá, ai cà phê không? 🌤️"
  "Chạy deadline xuyên màn đêm 🏃‍♂️💻"
  "Ready for the weekend! 🎉"
)

# 3. CHẾ ĐỘ PROBE ONLY
if [ "$PROBE_ONLY" = true ]; then
  echo "====> CHẾ ĐỘ KIỂM TRA (--probe-only): Kiểm tra Gateway, Auth & JWT"
  PROBE_USER="probe_bash_$((RANDOM % 9000 + 1000))"
  PROBE_EMAIL="${PROBE_USER}@demo.local"

  REG_RES=$(api_request "POST" "/api/auth/register" "{\"username\":\"$PROBE_USER\",\"email\":\"$PROBE_EMAIL\",\"password\":\"$DEMO_PASSWORD\",\"displayName\":\"Probe Tester\"}")
  if [[ "$REG_RES" == ERROR:* ]]; then
    echo "[FAIL] Đăng ký probe user thất bại!" >&2
    exit 1
  fi

  TOKEN=$(echo "$REG_RES" | jq -r '.accessToken // empty')
  SUB=$(decode_jwt "$TOKEN" "sub")
  ROLES=$(decode_jwt "$TOKEN" "roles")
  echo "  [OK] Đăng ký & Đăng nhập thành công!"
  echo "       sub (userId): $SUB"
  echo "       roles       : $ROLES"
  echo "====> KẾT QUẢ PROBE: HỆ THỐNG SẴN SÀNG!"
  exit 0
fi

# 4. CHUẨN BỊ ASSETS MẪU
echo "====> Chuẩn bị Assets (Avatars, Posts, Stories, Videos)..."
for i in $(seq 1 20); do
  av_path="$AVATAR_DIR/avatar_$i.jpg"
  if [ ! -f "$av_path" ]; then
    curl -s -o "$av_path" "https://i.pravatar.cc/300?img=$i" &
  fi
done
for i in $(seq 1 60); do
  post_path="$POST_IMG_DIR/post_$i.jpg"
  if [ ! -f "$post_path" ]; then
    curl -s -o "$post_path" "https://picsum.photos/seed/post$i/1080/1080" &
  fi
done
for i in $(seq 1 15); do
  st_path="$STORY_IMG_DIR/story_$i.jpg"
  if [ ! -f "$st_path" ]; then
    curl -s -o "$st_path" "https://picsum.photos/seed/story$i/1080/1920" &
  fi
done
wait

HAS_FFMPEG=false
if command -v ffmpeg &>/dev/null; then
  HAS_FFMPEG=true
  echo "  [OK] Tìm thấy FFmpeg. Đang tạo 12 video dọc 8s (1080x1920)..."
  for i in $(seq 1 12); do
    vid_path="$VIDEO_DIR/video_$i.mp4"
    if [ ! -f "$vid_path" ]; then
      img1="$STORY_IMG_DIR/story_${i}.jpg"
      [ -f "$img1" ] || img1="$POST_IMG_DIR/post_${i}.jpg"
      img2_idx=$(( (i * 3 - 1) % 60 + 1 ))
      img2="$POST_IMG_DIR/post_${img2_idx}.jpg"
      [ -f "$img2" ] || img2="$img1"
      if [ -f "$img1" ] && [ -f "$img2" ]; then
        filter="[0:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,format=yuv420p,zoompan=z='min(zoom+0.0012,1.15)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=135:s=1080x1920:fps=30[v0];[1:v]scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,format=yuv420p,zoompan=z='1.15-0.001*on':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=135:s=1080x1920:fps=30[v1];[v0][v1]xfade=transition=fade:duration=1:offset=3.5,format=yuv420p[v]"
        ffmpeg -y -loglevel error -i "$img1" -i "$img2" -f lavfi -i "anullsrc=r=44100:cl=stereo" \
          -filter_complex "$filter" -map "[v]" -map "2:a" \
          -t 8 -c:v libx264 -profile:v main -level 4.0 -pix_fmt yuv420p -b:v 1500k -c:a aac -b:a 128k -movflags +faststart "$vid_path" >/dev/null 2>&1
      else
        ffmpeg -y -loglevel error -f lavfi -i "color=c=0x111827:s=1080x1920:r=30" -f lavfi -i "anullsrc=r=44100:cl=stereo" \
          -t 8 -c:v libx264 -profile:v main -level 4.0 -pix_fmt yuv420p -b:v 1500k -c:a aac -b:a 128k -movflags +faststart -shortest "$vid_path" >/dev/null 2>&1
      fi
    fi
  done
else
  echo "  [WARN] FFmpeg không có sẵn trong PATH. Các bài video sẽ fallback sang ảnh."
  SKIPPED_REASONS+=("Tạo video bằng ffmpeg (thiếu ffmpeg CLI, fallback ảnh)")
fi

# 5. BƯỚC 1: TẠO 20 USER + TÀI KHOẢN DEMO
echo "====> Bước 1: Đăng ký & Đăng nhập 20 user + tài khoản 'demo'..."

# Khởi tạo mảng lưu user: USERNAME|EMAIL|USERID|TOKEN|IS_PRIVATE
declare -a USER_RECORDS=()

TARGETS=("demo")
for u in $(seq 1 "$USER_COUNT"); do TARGETS+=("user$u"); done

FAIL_STREAK=0
for uname in "${TARGETS[@]}"; do
  uemail="${uname}@demo.local"
  udisplay="$uname"
  if [ "$uname" = "demo" ]; then
    udisplay="Snapgram Demo Admin"
  else
    u_idx=$(echo "$uname" | tr -dc '0-9')
    if [ -n "$u_idx" ] && [ "$u_idx" -le 20 ]; then
      udisplay="${VIETNAMESE_NAMES[$((u_idx - 1))]}"
    fi
  fi

  # Thử register
  reg_payload=$(jq -n --arg un "$uname" --arg em "$uemail" --arg pw "$DEMO_PASSWORD" --arg dn "$udisplay" \
    '{username: $un, email: $em, password: $pw, displayName: $dn}')
  res=$(api_request "POST" "/api/auth/register" "$reg_payload")

  token=""
  userid=""
  if [[ "$res" != ERROR:* ]]; then
    token=$(echo "$res" | jq -r '.accessToken // empty')
    userid=$(echo "$res" | jq -r '.user.id // empty')
    COUNT_USERS_CREATED=$((COUNT_USERS_CREATED + 1))
    echo "  [OK] Đăng ký mới: $uname -> userId=$userid"
    FAIL_STREAK=0
  else
    # Thử login nếu đã tồn tại
    login_payload=$(jq -n --arg em "$uemail" --arg pw "$DEMO_PASSWORD" '{email: $em, password: $pw}')
    login_res=$(api_request "POST" "/api/auth/login" "$login_payload")
    if [[ "$login_res" != ERROR:* ]]; then
      token=$(echo "$login_res" | jq -r '.accessToken // empty')
      userid=$(echo "$login_res" | jq -r '.user.id // empty')
      COUNT_USERS_EXISTING=$((COUNT_USERS_EXISTING + 1))
      echo "  [OK] User có sẵn: $uname -> Đăng nhập thành công"
      FAIL_STREAK=0
    else
      FAIL_STREAK=$((FAIL_STREAK + 1))
      echo "  [FAIL] Không thể xác thực user: $uname" >&2
      if [ "$FAIL_STREAK" -ge 4 ]; then
        echo "FAIL-FAST: Gặp lỗi liên tiếp khi đăng ký/đăng nhập. Dừng script!" >&2
        exit 1
      fi
    fi
  fi

  if [ -n "$token" ] && [ -n "$userid" ]; then
    USER_RECORDS+=("$uname|$uemail|$userid|$token|0")
  fi
done

# 6. BƯỚC 2: THỰC THI NGOẠI LỆ SQL QUA DOCKER PSQL
echo "====> Bước 2: Thực thi ngoại lệ SQL (verify email, quyền ADMIN & tick xanh)..."
if command -v docker &>/dev/null; then
  CONTAINER="$DB_CONTAINER"
  if [ -z "$CONTAINER" ]; then
    CONTAINER=$(docker ps --format '{{.Names}}' 2>/dev/null | grep "postgres-auth" | head -n1)
  fi
  if [ -z "$CONTAINER" ]; then CONTAINER="snapgram-postgres-auth"; fi

  SQL_CMD="
  UPDATE auth_users SET email_verified = true WHERE email LIKE '%@demo.local';
  UPDATE auth_users SET role = 'USER', is_verified = true, verified_until = NOW() + INTERVAL '365 days' WHERE username = 'demo';
  DELETE FROM auth_user_roles WHERE user_id = (SELECT id FROM auth_users WHERE username = 'demo');
  "
  if docker exec -i "$CONTAINER" psql -U snapgram -d snapgram -c "$SQL_CMD" &>/dev/null; then
    echo "  [OK] Đã thực thi SQL cập nhật thành công trên container $CONTAINER."
  else
    echo "  [WARN] Lỗi khi thực thi psql trên container $CONTAINER."
    SKIPPED_REASONS+=("Docker exec psql thất bại")
  fi
else
  echo "  [WARN] Không có Docker CLI. Vui lòng tự chạy câu lệnh SQL verify email và ADMIN."
  SKIPPED_REASONS+=("Chạy SQL qua Docker (thiếu docker CLI)")
fi

# Chờ 3s cho Kafka đồng bộ projection
echo "====> Bước 3: Chờ 3 giây để Kafka đồng bộ projection..."
sleep 3

# 7. BƯỚC 4: AVATAR, PROFILE, PRIVATE & NOTES
echo "====> Bước 4: Cập nhật Avatar, Bio tiếng Việt, Private và Notes..."
TOTAL_USERS=${#USER_RECORDS[@]}
for idx in "${!USER_RECORDS[@]}"; do
  record="${USER_RECORDS[$idx]}"
  IFS='|' read -r uname uemail uid utoken is_priv <<< "$record"

  # Upload Avatar
  av_num=$(( (idx % 20) + 1 ))
  av_file="$AVATAR_DIR/avatar_$av_num.jpg"
  av_url=""
  if [ -f "$av_file" ]; then
    up_res=$(upload_media "$av_file" "$utoken")
    av_url=$(echo "$up_res" | jq -r '.url // empty')
    if [ -n "$av_url" ]; then COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1)); fi
  fi

  # Cập nhật Profile
  bio="${VIETNAMESE_BIOS[$((idx % ${#VIETNAMESE_BIOS[@]}))]}"
  web="https://${uname}.demo.vn"
  prof_payload=$(jq -n --arg dn "$uname" --arg bio "$bio" --arg web "$web" --arg av "$av_url" \
    '{displayName: $dn, bio: $bio, website: $web} + (if $av != "" then {avatarUrl: $av} else {} end)')
  api_request "PATCH" "/api/users/me" "$prof_payload" "$utoken" >/dev/null 2>&1
  COUNT_PROFILES_UPDATED=$((COUNT_PROFILES_UPDATED + 1))

  # Đặt 3 user private (không phải demo)
  if [ "$uname" != "demo" ] && [ "$idx" -le 3 ]; then
    api_request "PUT" "/api/users/me/privacy" '{"privateAccount": true}' "$utoken" >/dev/null 2>&1
    USER_RECORDS[$idx]="$uname|$uemail|$uid|$utoken|1"
    COUNT_PRIVATE_USERS=$((COUNT_PRIVATE_USERS + 1))
  fi

  # Note
  note_txt="${NOTES[$((idx % ${#NOTES[@]}))]}"
  note_payload=$(jq -n --arg c "$note_txt" '{content: $c}')
  api_request "POST" "/api/users/me/note" "$note_payload" "$utoken" >/dev/null 2>&1
  COUNT_NOTES_CREATED=$((COUNT_NOTES_CREATED + 1))
done
echo "  [OK] Đã cập nhật $COUNT_PROFILES_UPDATED profile, $COUNT_PRIVATE_USERS private, $COUNT_NOTES_CREATED notes."

# 8. BƯỚC 5: FOLLOW VÀ DUYỆT REQUEST
echo "====> Bước 5: Tạo quan hệ Follow và duyệt follow request..."
declare -a PENDING_REQUESTS=() # TARGET_INDEX:FOLLOW_ID

for i in "${!USER_RECORDS[@]}"; do
  IFS='|' read -r u1_name _ u1_id u1_token _ <<< "${USER_RECORDS[$i]}"
  for j in "${!USER_RECORDS[@]}"; do
    if [ "$i" -ne "$j" ]; then
      # Follow xác suất ~35% giữa các cặp user
      rval=$(next_random 100)
      if [ "$rval" -lt 35 ]; then
        IFS='|' read -r u2_name _ u2_id _ u2_priv <<< "${USER_RECORDS[$j]}"
        f_res=$(api_request "POST" "/api/follows/$u2_id" "" "$u1_token" 2>/dev/null)
        COUNT_FOLLOWS_CREATED=$((COUNT_FOLLOWS_CREATED + 1))
        f_status=$(echo "$f_res" | jq -r '.status // empty')
        f_id=$(echo "$f_res" | jq -r '.id // empty')
        if [ "$f_status" = "PENDING" ] && [ -n "$f_id" ]; then
          PENDING_REQUESTS+=("$j:$f_id")
        fi
      fi
    fi
  done
done

# Duyệt ~70% request
for pr in "${PENDING_REQUESTS[@]}"; do
  target_idx=$(echo "$pr" | cut -d':' -f1)
  req_id=$(echo "$pr" | cut -d':' -f2)
  IFS='|' read -r _ _ _ target_token _ <<< "${USER_RECORDS[$target_idx]}"
  rval=$(next_random 100)
  if [ "$rval" -lt 70 ]; then
    api_request "POST" "/api/follows/requests/$req_id/accept" "" "$target_token" >/dev/null 2>&1
    COUNT_FOLLOWS_ACCEPTED=$((COUNT_FOLLOWS_ACCEPTED + 1))
  fi
done
echo "  [OK] Đã tạo $COUNT_FOLLOWS_CREATED follow, duyệt thành công $COUNT_FOLLOWS_ACCEPTED request."

# 9. BƯỚC 6: SEED 60 BÀI VIẾT THEO PHÂN BỐ
echo "====> Bước 6: Đăng 60 bài viết đa dạng..."
declare -a CREATED_POSTS=() # POST_ID|IS_VIDEO

for p in $(seq 1 "$POST_COUNT"); do
  auth_idx=$(( (p - 1) % TOTAL_USERS ))
  IFS='|' read -r a_name _ a_id a_token _ <<< "${USER_RECORDS[$auth_idx]}"
  cap_idx=$(( (p - 1) % ${#CAPTIONS[@]} ))
  caption="${CAPTIONS[$cap_idx]}"
  vis="PUBLIC"
  if [ "$p" -gt 51 ]; then vis="FOLLOWERS"; fi

  post_body=""
  is_vid=0

  if [ "$p" -le 25 ]; then
    # 25 bài 1 ảnh
    img_num=$(( (p % 60) + 1 ))
    up_res=$(upload_media "$POST_IMG_DIR/post_$img_num.jpg" "$a_token")
    COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
    mid=$(echo "$up_res" | jq -r '.id // empty')
    murl=$(echo "$up_res" | jq -r '.url // empty')
    mthumb=$(echo "$up_res" | jq -r '.thumbnailUrl // empty')
    post_body=$(jq -n --arg c "$caption" --arg v "$vis" --arg mid "$mid" --arg murl "$murl" --arg mt "$mthumb" \
      '{caption: $c, visibility: $v, contentType: "IMAGE", mediaList: [{mediaId: $mid, mediaUrl: $murl, thumbnailUrl: $mt, mediaType: "IMAGE", sortOrder: 0}]}')

  elif [ "$p" -le 40 ]; then
    # 15 bài carousel 2-3 ảnh
    up_res1=$(upload_media "$POST_IMG_DIR/post_$(( (p * 2) % 60 + 1 )).jpg" "$a_token")
    up_res2=$(upload_media "$POST_IMG_DIR/post_$(( (p * 2 + 1) % 60 + 1 )).jpg" "$a_token")
    COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 2))
    post_body=$(jq -n --arg c "$caption" --arg v "$vis" \
      --arg m1 "$(echo "$up_res1" | jq -r '.url')" --arg id1 "$(echo "$up_res1" | jq -r '.id')" \
      --arg m2 "$(echo "$up_res2" | jq -r '.url')" --arg id2 "$(echo "$up_res2" | jq -r '.id')" \
      '{caption: $c, visibility: $v, contentType: "CAROUSEL", mediaList: [
        {mediaId: $id1, mediaUrl: $m1, mediaType: "IMAGE", sortOrder: 0},
        {mediaId: $id2, mediaUrl: $m2, mediaType: "IMAGE", sortOrder: 1}
      ]}')

  elif [ "$p" -le 52 ]; then
    # 12 bài 1 video
    v_num=$(( (p - 41) % 12 + 1 ))
    v_file="$VIDEO_DIR/video_$v_num.mp4"
    if [ "$HAS_FFMPEG" = true ] && [ -f "$v_file" ]; then
      up_res=$(upload_media "$v_file" "$a_token")
      COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
      vid_url=$(echo "$up_res" | jq -r '.url // empty')
      vid_id=$(echo "$up_res" | jq -r '.id // empty')
      post_body=$(jq -n --arg c "$caption" --arg v "$vis" --arg mid "$vid_id" --arg murl "$vid_url" \
        '{caption: $c, visibility: $v, contentType: "VIDEO", mediaList: [{mediaId: $mid, mediaUrl: $murl, mediaType: "VIDEO", sortOrder: 0, durationSec: 8}]}')
      is_vid=1
    else
      up_res=$(upload_media "$POST_IMG_DIR/post_$v_num.jpg" "$a_token")
      COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
      post_body=$(jq -n --arg c "$caption" --arg v "$vis" --arg mid "$(echo "$up_res" | jq -r '.id')" --arg murl "$(echo "$up_res" | jq -r '.url')" \
        '{caption: $c, visibility: $v, contentType: "IMAGE", mediaList: [{mediaId: $mid, mediaUrl: $murl, mediaType: "IMAGE", sortOrder: 0}]}')
    fi

  elif [ "$p" -le 57 ]; then
    # 5 bài carousel trộn ảnh + video
    v_num=$(( (p % 12) + 1 ))
    v_file="$VIDEO_DIR/video_$v_num.mp4"
    up_img=$(upload_media "$POST_IMG_DIR/post_$(( p % 60 + 1 )).jpg" "$a_token")
    COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
    if [ "$HAS_FFMPEG" = true ] && [ -f "$v_file" ]; then
      up_vid=$(upload_media "$v_file" "$a_token")
      COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
      post_body=$(jq -n --arg c "$caption" --arg v "$vis" \
        --arg id1 "$(echo "$up_img" | jq -r '.id')" --arg u1 "$(echo "$up_img" | jq -r '.url')" \
        --arg id2 "$(echo "$up_vid" | jq -r '.id')" --arg u2 "$(echo "$up_vid" | jq -r '.url')" \
        '{caption: $c, visibility: $v, contentType: "CAROUSEL", mediaList: [
          {mediaId: $id1, mediaUrl: $u1, mediaType: "IMAGE", sortOrder: 0},
          {mediaId: $id2, mediaUrl: $u2, mediaType: "VIDEO", sortOrder: 1, durationSec: 8}
        ]}')
      is_vid=1
    else
      post_body=$(jq -n --arg c "$caption" --arg v "$vis" --arg mid "$(echo "$up_img" | jq -r '.id')" --arg murl "$(echo "$up_img" | jq -r '.url')" \
        '{caption: $c, visibility: $v, contentType: "IMAGE", mediaList: [{mediaId: $mid, mediaUrl: $murl, mediaType: "IMAGE", sortOrder: 0}]}')
    fi

  else
    # 3 bài chỉ text
    post_body=$(jq -n --arg c "$caption" --arg v "$vis" '{caption: $c, visibility: $v, contentType: "TEXT"}')
  fi

  created_res=$(api_request "POST" "/api/posts" "$post_body" "$a_token")
  post_id=$(echo "$created_res" | jq -r '.id // empty')
  if [ -n "$post_id" ]; then
    CREATED_POSTS+=("$post_id|$is_vid")
    COUNT_POSTS_CREATED=$((COUNT_POSTS_CREATED + 1))
  fi
done
echo "  [OK] Đã đăng thành công $COUNT_POSTS_CREATED/$POST_COUNT bài viết."

# 10. BƯỚC 7: TẠO TƯƠNG TÁC (LIKE, COMMENT, REPLY, LIKE COMMENT, SAVE, VIDEO VIEW)
echo "====> Bước 7: Tạo tương tác Like, Comment, Reply, Save, Video view..."
for p_item in "${CREATED_POSTS[@]}"; do
  pid=$(echo "$p_item" | cut -d'|' -f1)
  is_v=$(echo "$p_item" | cut -d'|' -f2)

  # Like (0..5 likes ngẫu nhiên)
  for u_rec in "${USER_RECORDS[@]}"; do
    IFS='|' read -r _ _ _ u_tok _ <<< "$u_rec"
    rval=$(next_random 100)
    if [ "$rval" -lt 25 ]; then
      api_request "POST" "/api/posts/$pid/interact" '{"type":"LIKE"}' "$u_tok" >/dev/null 2>&1
      COUNT_LIKES_CREATED=$((COUNT_LIKES_CREATED + 1))
    fi
  done

  # Comment (0..2 comments)
  c_res=$(api_request "POST" "/api/posts/$pid/comments" '{"content":"Ảnh đẹp và nhiều năng lượng quá! ✨"}' "${USER_RECORDS[1]##*|}")
  c_id=$(echo "$c_res" | jq -r '.id // empty')
  if [ -n "$c_id" ]; then
    COUNT_COMMENTS_CREATED=$((COUNT_COMMENTS_CREATED + 1))

    # Reply ~35%
    rval=$(next_random 100)
    if [ "$rval" -lt 35 ]; then
      api_request "POST" "/api/posts/$pid/comments" "{\"content\":\"Cảm ơn bạn nhiều nha ❤️\",\"parentCommentId\":\"$c_id\"}" "${USER_RECORDS[0]##*|}" >/dev/null 2>&1
      COUNT_REPLIES_CREATED=$((COUNT_REPLIES_CREATED + 1))
    fi

    # Like comment
    api_request "POST" "/api/posts/$pid/comments/$c_id/like" "" "${USER_RECORDS[2]##*|}" >/dev/null 2>&1
    COUNT_COMMENT_LIKES=$((COUNT_COMMENT_LIKES + 1))
  fi

  # Save post ~15%
  rval=$(next_random 100)
  if [ "$rval" -lt 15 ]; then
    api_request "POST" "/api/saved/$pid" "" "${USER_RECORDS[3]##*|}" >/dev/null 2>&1
    COUNT_SAVES_CREATED=$((COUNT_SAVES_CREATED + 1))
  fi

  # Video view & skip
  if [ "$is_v" -eq 1 ]; then
    api_request "POST" "/api/posts/$pid/interact" '{"type":"VIEW","watchTimeRatio":0.85}' "${USER_RECORDS[0]##*|}" >/dev/null 2>&1
    COUNT_VIEWS_CREATED=$((COUNT_VIEWS_CREATED + 1))
    api_request "POST" "/api/posts/$pid/interact" '{"type":"SKIP"}' "${USER_RECORDS[1]##*|}" >/dev/null 2>&1
    COUNT_SKIPS_CREATED=$((COUNT_SKIPS_CREATED + 1))
  fi
done
echo "  [OK] Hoàn tất tương tác: $COUNT_LIKES_CREATED likes, $COUNT_COMMENTS_CREATED comments, $COUNT_REPLIES_CREATED replies."

# 11. BƯỚC 8: SEED 15 STORIES
echo "====> Bước 8: Tạo 15 Stories (ảnh, video)..."
for s in $(seq 1 "$STORY_COUNT"); do
  auth_idx=$(( (s - 1) % TOTAL_USERS ))
  IFS='|' read -r _ _ _ a_token _ <<< "${USER_RECORDS[$auth_idx]}"

  vis="PUBLIC"
  if [ $((s % 3)) -eq 1 ]; then vis="FOLLOWERS"; elif [ $((s % 3)) -eq 2 ]; then vis="CLOSE_FRIENDS"; fi

  st_file="$STORY_IMG_DIR/story_$s.jpg"
  if [ -f "$st_file" ]; then
    up_res=$(upload_media "$st_file" "$a_token")
    COUNT_MEDIA_UPLOADED=$((COUNT_MEDIA_UPLOADED + 1))
    murl=$(echo "$up_res" | jq -r '.url // empty')
    mid=$(echo "$up_res" | jq -r '.id // empty')
    st_body=$(jq -n --arg u "$murl" --arg id "$mid" --arg v "$vis" --arg c "Khoảnh khắc số $s ✨" \
      '{mediaUrl: $u, mediaId: $id, mediaType: "IMAGE", caption: $c, visibility: $v}')
    api_request "POST" "/api/stories" "$st_body" "$a_token" >/dev/null 2>&1
    COUNT_STORIES_CREATED=$((COUNT_STORIES_CREATED + 1))
  fi
done
echo "  [OK] Đã tạo $COUNT_STORIES_CREATED/$STORY_COUNT stories."

# 12. BƯỚC 9: KÍCH HOẠT SPRING BATCH (RECOMMENDER)
if [ "$SKIP_BATCH" = false ]; then
  echo "====> Bước 9: Kích hoạt Spring Batch jobs (recommender)..."
  ADMIN_EMAIL="${ADMIN_INITIAL_EMAIL:-admin@snapgram.com}"
  ADMIN_PASSWORD="${ADMIN_INITIAL_PASSWORD:-admin123}"
  admin_res=$(api_request "POST" "/api/auth/admin/login" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")
  admin_token=$(echo "$admin_res" | jq -r '.accessToken // empty' 2>/dev/null || true)
  for job in trendingPostsJob userRecommendationJob; do
    echo "  -> Đang chạy job $job..."
    run_res=$(api_request "POST" "/api/admin/batch/jobs/$job/run" "" "$admin_token")
    exec_id=$(echo "$run_res" | jq -r '.jobExecutionId // empty')
    if [ -n "$exec_id" ]; then
      COUNT_BATCH_RUN=$((COUNT_BATCH_RUN + 1))
      echo "     Job $job đã kích hoạt (ExecutionId: $exec_id)."
    fi
  done
else
  SKIPPED_REASONS+=("Kích hoạt Batch (--skip-batch)")
fi

# 13. TỔNG KẾT
echo ""
echo "========================================================================"
echo "                BẢNG TỔNG KẾT DỮ LIỆU SEED DEMO (BASH)"
echo "========================================================================"
printf "  %-30s : %s\n" "Người dùng mới tạo" "$COUNT_USERS_CREATED"
printf "  %-30s : %s\n" "Người dùng đã có sẵn" "$COUNT_USERS_EXISTING"
printf "  %-30s : %s\n" "Profile đã cập nhật" "$COUNT_PROFILES_UPDATED"
printf "  %-30s : %s\n" "Tài khoản Private" "$COUNT_PRIVATE_USERS"
printf "  %-30s : %s\n" "Ghi chú (Notes)" "$COUNT_NOTES_CREATED"
printf "  %-30s : %s\n" "Quan hệ Follow đã tạo" "$COUNT_FOLLOWS_CREATED"
printf "  %-30s : %s\n" "Follow Request đã duyệt" "$COUNT_FOLLOWS_ACCEPTED"
printf "  %-30s : %s\n" "Media đã tải lên" "$COUNT_MEDIA_UPLOADED"
printf "  %-30s : %s\n" "Bài viết đã đăng" "$COUNT_POSTS_CREATED"
printf "  %-30s : %s\n" "Lượt thích bài viết" "$COUNT_LIKES_CREATED"
printf "  %-30s : %s\n" "Bình luận (Comments)" "$COUNT_COMMENTS_CREATED"
printf "  %-30s : %s\n" "Câu trả lời (Replies)" "$COUNT_REPLIES_CREATED"
printf "  %-30s : %s\n" "Lượt thích bình luận" "$COUNT_COMMENT_LIKES"
printf "  %-30s : %s\n" "Bài viết đã lưu (Saves)" "$COUNT_SAVES_CREATED"
printf "  %-30s : %s\n" "Lượt xem video (Views)" "$COUNT_VIEWS_CREATED"
printf "  %-30s : %s\n" "Lượt bỏ qua video (Skips)" "$COUNT_SKIPS_CREATED"
printf "  %-30s : %s\n" "Stories đã tạo" "$COUNT_STORIES_CREATED"
printf "  %-30s : %s\n" "Spring Batch jobs đã chạy" "$COUNT_BATCH_RUN"

if [ ${#SKIPPED_REASONS[@]} -gt 0 ]; then
  echo ""
  echo "  Các mục bị bỏ qua / ghi chú:"
  for reason in "${SKIPPED_REASONS[@]}"; do
    echo "   - $reason"
  done
fi

echo ""
echo "------------------------------------------------------------------------"
echo "  LƯU Ý QUAN TRỌNG VỀ DEMO:"
echo "  1. Story và Note trong Instagram/Snapgram sẽ tự động hết hạn sau 24 GIỜ."
echo "     -> Khuyến nghị: Chạy lại script ngay trước buổi demo."
echo "  2. Đăng nhập tài khoản demo:"
echo "     - Email:    demo@demo.local"
echo "     - Password: $DEMO_PASSWORD"
echo "     - Quyền:    USER (kèm Verified Blue Tick)"
echo "========================================================================"
