# Kịch Bản & Hướng Dẫn Demo Spring Batch Trong Recommender Service

> **Dành cho:** Buổi thuyết trình / bảo vệ đồ án môn học & tốt nghiệp.  
> **Dịch vụ tích hợp:** `recommender-service` (Port: `8090`, Gateway Port: `8080`).  
> **Mục tiêu:** Thể hiện trực quan sức mạnh của Spring Batch: **Chunk Processing**, **Fault Tolerance (Skip)**, **State Management & Restart Capability**, cùng **Job Monitoring / Metadata**.

---

## 1. Chuẩn Bị Môi Trường

### 1.1 Khởi động Hạ tầng (Docker Compose)
Chạy PostgreSQL, Redis, Kafka từ thư mục `infra/`:
```bash
cd infra
docker compose up -d postgres-recommender redis kafka
```

### 1.2 Khởi động `recommender-service`
Có thể chạy trực tiếp bằng Maven hoặc Docker:
```bash
# Cách 1: Chạy trực tiếp qua Maven từ thư mục gốc
mvn spring-boot:run -pl recommender-service

# Hoặc Cách 2: Chạy qua Docker (Port 8090)
docker compose -f infra/docker-compose.yml up -d --build recommender-service
```

> ⚠️ **CẢNH BÁO QUAN TRỌNG VỀ XÁC THỰC:**  
> Trong buổi demo, các lệnh `curl` gọi trực tiếp vào cổng nội bộ `8090` của `recommender-service` kèm các header giả lập:
> ```bash
> -H "X-User-Id: 00000000-0000-0000-0000-000000000000" \
> -H "X-User-Roles: ADMIN,USER"
> ```
> Khi lên **Production**, toàn bộ client bắt buộc phải đi qua **API Gateway (Port 8080)**. API Gateway sẽ chịu trách nhiệm xác thực JWT tập trung, trích xuất thông tin người dùng từ token và tự động inject các header `X-User-Id`, `X-User-Roles` trước khi forward vào các downstream services.
> 
> *(Mẹo môi trường Windows/PowerShell: Nên dùng `127.0.0.1:8090` thay vì `localhost:8090` để tránh độ trễ phân giải IPv6 `[::1]`)*.

---

## 2. Kịch Bản Demo Liền Mạch (Sử Dụng Biến Shell & Số Liệu Thực Tế)

Toàn bộ các bước dưới đây được thiết kế để **chạy liên tục trên một cơ sở dữ liệu duy nhất**, tự động trích xuất `jobExecutionId` vào biến shell bằng `jq`.

```bash
# Cấu hình biến môi trường dùng chung
BASE_URL="http://127.0.0.1:8090"
ADMIN_HEADERS=(-H "X-User-Id: 00000000-0000-0000-0000-000000000000" -H "X-User-Roles: ADMIN,USER")
```

---

### 📌 BƯỚC 1: Seed Dữ Liệu Mẫu (5.000 Bản Ghi)
*Mục đích:* Tạo nhanh dữ liệu tương tác (5.000 bài viết, 10.000 lượt like/view, ~500 quan hệ follow) có cài sẵn **3 bài viết lỗi cố ý** (chứa cờ `DEMO_MALFORMED`, được gán thời gian lùi về 31 ngày trước để luôn nằm ở cuối danh sách đọc) nhằm phục vụ kiểm thử tính năng **Skip**.

**Lệnh thực thi:**
```bash
curl -s -X POST "$BASE_URL/api/admin/batch/demo/seed?count=5000" "${ADMIN_HEADERS[@]}" | jq .
```

**Kết quả thực tế trả về:**
```json
{
  "postsCreated": 5000,
  "interactionsCreated": 10000,
  "followsCreated": 497,
  "malformedPostsCreated": 3,
  "message": "Đã nạp thành công dữ liệu mẫu cho Spring Batch Demo!"
}
```

---

### 📌 BƯỚC 2: Kích Hoạt Job & Thể Hiện Chunk Processing + Skip
*Mục đích:* Kích hoạt job `trendingPostsJob`. Trên console log hiển thị từng **Chunk Commit** (mỗi chunk 20 items) và **SkipListener** tự động bắt các bản ghi lỗi mà job không bị dừng.

**Lệnh thực thi:**
```bash
RUN_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run" "${ADMIN_HEADERS[@]}")
EXEC_ID_1=$(echo "$RUN_RESP" | jq -r .jobExecutionId)
echo "Kích hoạt thành công trendingPostsJob - Execution ID: $EXEC_ID_1"
```

**Kết quả API trả về ngay lập tức (Bất đồng bộ - Non-blocking):**
```json
{
  "jobExecutionId": 1,
  "jobName": "trendingPostsJob",
  "status": "STARTING",
  "exitCode": "UNKNOWN",
  "exitDescription": "",
  "createTime": "2026-11-05T14:16:16.407218",
  "steps": []
}
```

**Quan sát Console Log thực tế:**
```text
================================================================================
🚀 [SPRING BATCH START] Job: trendingPostsJob (ExecutionId: 1)
▶ Start Time: 2026-11-05T14:16:16.443292
▶ Parameters: {triggerSource=ADMIN_API, timestamp=1793885501118}
================================================================================
📦 [CHUNK COMMITTED] Step: [trendingPostsStep] | Chunk #1 | Read: 20, Written: 20, Skipped: 0
📦 [CHUNK COMMITTED] Step: [trendingPostsStep] | Chunk #2 | Read: 40, Written: 40, Skipped: 0
...
📦 [CHUNK COMMITTED] Step: [trendingPostsStep] | Chunk #249 | Read: 4980, Written: 4980, Skipped: 0
⚠️ [CHUNK ROLLBACK] Step: [trendingPostsStep] encountered an error during chunk execution
⚠️ [SKIP-ON-PROCESS] Bỏ qua item [...] do lỗi nghiệp vụ (MalformedPostException): Bản ghi bài viết chứa nội dung bẩn cố ý 'DEMO_MALFORMED'
⚠️ [SKIP-ON-PROCESS] Bỏ qua item [...] do lỗi nghiệp vụ (MalformedPostException): Bản ghi bài viết chứa nội dung bẩn cố ý 'DEMO_MALFORMED'
⚠️ [SKIP-ON-PROCESS] Bỏ qua item [...] do lỗi nghiệp vụ (MalformedPostException): Bản ghi bài viết chứa nội dung bẩn cố ý 'DEMO_MALFORMED'
📦 [CHUNK COMMITTED] Step: [trendingPostsStep] | Chunk #250 | Read: 5000, Written: 4997, Skipped: 3
📦 [CHUNK COMMITTED] Step: [trendingPostsStep] | Chunk #251 | Read: 5000, Written: 4997, Skipped: 3
🔥 [REDIS CACHE UPDATED] Đã lưu Top 50 bài viết trending (rank 1-50) vào key 'trending:posts' (TTL: PT6H)
================================================================================
✅ [SPRING BATCH END] Job: trendingPostsJob (ExecutionId: 1)
▶ Status: COMPLETED | ExitCode: COMPLETED
▶ Duration: 8253 ms (8.25 sec)
▶ Metrics: Read=5000, Written=4997, Skipped=3, Commits=251
================================================================================
```

---

### 📌 BƯỚC 3: Giám Sát Chi Tiết Qua Monitoring API (Poll Đến Khi COMPLETED)
*Mục đích:* Thăm dò trạng thái cho đến khi hoàn tất và xem số liệu thực tế được lưu vào PostgreSQL metadata:

**Lệnh thực thi:**
```bash
# Polling chờ Job COMPLETED
while true; do
  STATUS=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$EXEC_ID_1" "${ADMIN_HEADERS[@]}" | jq -r .status)
  echo "Trạng thái hiện tại: $STATUS"
  [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ] && break
  sleep 1
done

# Xem kết quả chi tiết
curl -s -X GET "$BASE_URL/api/admin/batch/executions/$EXEC_ID_1" "${ADMIN_HEADERS[@]}" | jq .
```

**Kết quả JSON thực tế:**
```json
{
  "jobExecutionId": 1,
  "jobName": "trendingPostsJob",
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "exitDescription": "",
  "createTime": "2026-11-05T14:16:16.407218",
  "startTime": "2026-11-05T14:16:16.443292",
  "endTime": "2026-11-05T14:16:24.69725",
  "durationMs": 8253,
  "steps": [
    {
      "stepName": "trendingPostsStep",
      "status": "COMPLETED",
      "exitCode": "COMPLETED",
      "readCount": 5000,
      "writeCount": 4997,
      "commitCount": 251,
      "rollbackCount": 3,
      "readSkipCount": 0,
      "processSkipCount": 3,
      "writeSkipCount": 0,
      "totalSkipCount": 3
    }
  ]
}
```

---

### 📌 BƯỚC 4: Kiểm Chứng Tính Idempotency (Chạy Lần 2 Không Bị Tăng Dữ Liệu)
*Mục đích:* Khi chạy lại Job một lần nữa trên cùng database, nhờ ID tất định (`id = post.getPostId()`) và ràng buộc unique `uq_recommender_trending_post_id`, số bản ghi trong bảng kết quả không tăng:

**1. Kiểm tra số bản ghi trong DB trước khi chạy lần 2:**
```bash
docker exec snapgram-postgres-recommender psql -U snapgram -d snapgram -c "SELECT count(*), count(DISTINCT post_id) FROM recommender_trending_post;"
```
*Kết quả:* `4997 | 4997`.

**2. Kích hoạt Job lần 2 và chờ hoàn thành:**
```bash
EXEC_ID_2=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run" "${ADMIN_HEADERS[@]}" | jq -r .jobExecutionId)
echo "Kích hoạt lần 2 - Execution ID: $EXEC_ID_2"

while true; do
  STATUS=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$EXEC_ID_2" "${ADMIN_HEADERS[@]}" | jq -r .status)
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 1
done
echo "Execution $EXEC_ID_2 đã COMPLETED."
```

**3. Kiểm tra lại DB sau lần 2:**
```bash
docker exec snapgram-postgres-recommender psql -U snapgram -d snapgram -c "SELECT count(*), count(DISTINCT post_id) FROM recommender_trending_post;"
```
*Kết quả:* `4997 | 4997` (Số dòng giữ nguyên tuyệt đối, không có bản ghi nào bị trùng lặp).

---

### 📌 BƯỚC 5: Biểu Diễn Khả Năng RESTART (Phục Hồi Sự Cố Thông Minh)
*Mục đích:* Mô phỏng sự cố ứng dụng bị ngắt quãng tại item thứ 45. Spring Batch commit 2 chunk đầu (40 bản ghi) và dừng lại ở trạng thái `FAILED`. Sau đó khi restart, Spring Batch **tự động chạy tiếp từ item thứ 41**, không đọc và không ghi lại 40 items đầu đã commit!

**1. Chạy Job cố tình gây lỗi tại item thứ 45:**
```bash
FAIL_EXEC_ID=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run?failAtItem=45" "${ADMIN_HEADERS[@]}" | jq -r .jobExecutionId)
echo "Execution gây lỗi: $FAIL_EXEC_ID"

# Chờ execution chuyển sang FAILED
while true; do
  STATUS=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID" "${ADMIN_HEADERS[@]}" | jq -r .status)
  [ "$STATUS" = "FAILED" ] && break
  sleep 1
done
```

**2. Xem trạng thái Execution bị FAILED:**
```bash
curl -s -X GET "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID" "${ADMIN_HEADERS[@]}" | jq .
```
*Kết quả thực tế (`jobExecutionId = 3`):*
```json
{
  "jobExecutionId": 3,
  "jobName": "trendingPostsJob",
  "status": "FAILED",
  "exitCode": "FAILED",
  "exitDescription": "Caused by: java.lang.IllegalStateException: Cố tình mô phỏng sự cố hệ thống tại item #45 để demo khả năng RESTART của Spring Batch!",
  "steps": [
    {
      "stepName": "trendingPostsStep",
      "status": "FAILED",
      "readCount": 60,
      "writeCount": 40,
      "commitCount": 2,
      "rollbackCount": 2
    }
  ]
}
```
*Phân tích:* Đã commit thành công 2 chunk = 40 items. Lỗi xảy ra ở chunk 3 (item 45), chunk 3 được rollback an toàn (`commitCount = 2`).

**3. Khởi động lại (Restart) Execution bị FAILED:**
```bash
RESTART_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID/restart" "${ADMIN_HEADERS[@]}")
RESTART_EXEC_ID=$(echo "$RESTART_RESP" | jq -r .jobExecutionId)
echo "Job đã được Restart thành công với Execution ID mới: $RESTART_EXEC_ID (khác với ID cũ $FAIL_EXEC_ID)"
```

**4. Xem số liệu thực tế LẦN RESTART (Execution ID = 4):**
```bash
curl -s -X GET "$BASE_URL/api/admin/batch/executions/$RESTART_EXEC_ID" "${ADMIN_HEADERS[@]}" | jq .
```
*Kết quả thực tế sau Restart (`jobExecutionId = 4`):*
```json
{
  "jobExecutionId": 4,
  "jobName": "trendingPostsJob",
  "status": "COMPLETED",
  "exitCode": "COMPLETED",
  "steps": [
    {
      "stepName": "trendingPostsStep",
      "status": "COMPLETED",
      "readCount": 4960,
      "writeCount": 4957,
      "commitCount": 249,
      "totalSkipCount": 3
    }
  ]
}
```

*Số liệu chứng minh tuyệt đối Reader không đọc lại 40 items đầu:*
- `readCount`: **4960** (5.000 tổng số bài − 40 bài đã commit ở lần trước = đúng **4.960** bài).
- `writeCount`: **4957** (4.997 bài hợp lệ − 40 bài đã ghi ở lần trước = đúng **4.957** bài).
- `commitCount`: **249** (251 tổng commits − 2 commits đã xong ở lần trước = đúng **249** commits).
- Tổng lũy kế: $40 + 4.960 = 5.000$ items đọc, $40 + 4.957 = 4.997$ items ghi, $2 + 249 = 251$ commits.

---

### 📌 BƯỚC 6: Demo Job 2 (`userRecommendationJob`) & Đọc Kết Quả
Gợi ý danh sách bạn bè tiềm năng theo mạng xã hội (Friend-of-Friends):

**1. Chạy Job gợi ý người dùng:**
```bash
USER_EXEC_ID=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/userRecommendationJob/run" "${ADMIN_HEADERS[@]}" | jq -r .jobExecutionId)
echo "Kích hoạt userRecommendationJob - Execution ID: $USER_EXEC_ID"

while true; do
  STATUS=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$USER_EXEC_ID" "${ADMIN_HEADERS[@]}" | jq -r .status)
  [ "$STATUS" = "COMPLETED" ] && break
  sleep 1
done
curl -s -X GET "$BASE_URL/api/admin/batch/executions/$USER_EXEC_ID" "${ADMIN_HEADERS[@]}" | jq .
```

*Kết quả thực tế (`jobExecutionId = 5`):*
```json
{
  "jobExecutionId": 5,
  "jobName": "userRecommendationJob",
  "status": "COMPLETED",
  "steps": [
    {
      "stepName": "userRecommendationStep",
      "status": "COMPLETED",
      "readCount": 51,
      "writeCount": 50,
      "commitCount": 3,
      "rollbackCount": 1,
      "totalSkipCount": 1
    }
  ]
}
```
*Phân tích:* Đọc 51 users, bỏ qua 1 user dị thường `00000000-0000-0000-0000-000000000001`, ghi nhận 50 users hợp lệ.

**2. Người dùng truy vấn danh sách bạn bè gợi ý (Chọn user có ít nhất 3 gợi ý):**
```bash
# Lấy động ID của user có từ 3 gợi ý trở lên trong DB
SAMPLE_USER=$(docker exec snapgram-postgres-recommender psql -U snapgram -d snapgram -t -c "SELECT user_id FROM recommender_user_recommendation GROUP BY user_id HAVING count(*) >= 3 LIMIT 1;" | tr -d '[:space:]')
echo "User kiểm tra: $SAMPLE_USER"

curl -s -X GET "$BASE_URL/api/recommendations/users?limit=3" \
  -H "X-User-Id: $SAMPLE_USER" \
  -H "X-User-Roles: USER" | jq .
```

*Kết quả thực tế:*
```json
[
  {
    "userId": "009c5c07-0c96-4697-9881-a2c3360e18ac",
    "recommendedUserId": "574f4e35-ca0e-4883-88eb-9cc4b4f57446",
    "score": 0.8,
    "mutualFriendsCount": 3,
    "reason": "Có 3 người bạn chung theo dõi",
    "calculatedAt": "2026-11-05T14:19:10.990Z"
  },
  {
    "userId": "009c5c07-0c96-4697-9881-a2c3360e18ac",
    "recommendedUserId": "3a0eb5ff-fa8a-43af-8111-a30f060c3586",
    "score": 0.7,
    "mutualFriendsCount": 2,
    "reason": "Có 2 người bạn chung theo dõi",
    "calculatedAt": "2026-11-05T14:19:10.990Z"
  },
  {
    "userId": "009c5c07-0c96-4697-9881-a2c3360e18ac",
    "recommendedUserId": "09a98122-f258-4d11-ad6b-35cd4da5ab69",
    "score": 0.7,
    "mutualFriendsCount": 2,
    "reason": "Có 2 người bạn chung theo dõi",
    "calculatedAt": "2026-11-05T14:19:10.990Z"
  }
]
```

---

### 📌 BƯỚC 7: Đối Chiếu Trực Tiếp Giữa Redis Cache và PostgreSQL Database
So sánh đối chiếu Top 5 bài viết thịnh hành:

**1. Kiểm tra trong Redis Cache:**
```bash
docker exec snapgram-redis redis-cli get trending:posts | jq '.[0:5]'
```
*Kết quả:*
```json
[
  { "rank": 1, "postId": "89baa4c9-2453-4466-ac38-c660d064b076", "score": 0.637, "likesCount": 3 },
  { "rank": 2, "postId": "5f64520c-5f52-4d40-979e-02c22df8631d", "score": 0.631, "likesCount": 1 },
  { "rank": 3, "postId": "029967f3-3326-41a6-ba14-5a2f8badb7cc", "score": 0.607, "likesCount": 0 },
  { "rank": 4, "postId": "2a7173d1-7564-474e-98b5-a10dda341054", "score": 0.604, "likesCount": 1 },
  { "rank": 5, "postId": "de1e2616-c96d-4417-bba4-6ae4038e495e", "score": 0.604, "likesCount": 1 }
]
```

**2. Truy vấn trực tiếp PostgreSQL:**
```bash
docker exec snapgram-postgres-recommender psql -U snapgram -d snapgram -c "SELECT rank, post_id, score, likes_count FROM recommender_trending_post ORDER BY score DESC, post_id ASC LIMIT 5;"
```
*Kết quả:*
```text
 rank |               post_id                | score | likes_count 
------+--------------------------------------+-------+-------------
    1 | 89baa4c9-2453-4466-ac38-c660d064b076 | 0.637 |           3
    2 | 5f64520c-5f52-4d40-979e-02c22df8631d | 0.631 |           1
    3 | 029967f3-3326-41a6-ba14-5a2f8badb7cc | 0.607 |           0
    4 | 2a7173d1-7564-474e-98b5-a10dda341054 | 0.604 |           1
    5 | de1e2616-c96d-4417-bba4-6ae4038e495e | 0.604 |           1
(5 rows)
```
*Kết luận:* Cả Redis Cache và Database đều khớp 100% về thứ tự, điểm số, và gán rank từ 1 đến 5.

---

## 3. Bộ Câu Hỏi Phản Biện Mở Rộng Của Hội Đồng & Gợi Ý Trả Lời

#### ❓ Câu 1: Tại sao phải dùng Spring Batch mà không tự viết vòng lặp phân trang hoặc `@Scheduled` thông thường?
> **Trả lời:**
> - Về mặt kỹ thuật, tự viết vòng lặp `while/for` kết hợp `Pageable` trong `@Scheduled` vẫn có thể tính toán được. Tuy nhiên, lập trình viên sẽ phải **tự xây dựng lại toàn bộ các bài toán nền tảng phức tạp**:
>   1. **Lưu vết trạng thái (State Persistence) để Restart:** Nếu xử lý 100.000 bản ghi mà mất điện ở bản ghi 80.000, tự viết vòng lặp sẽ phải chạy lại từ 0 hoặc phải tự tạo bảng lưu offset. Spring Batch đã tích hợp sẵn cơ chế lưu `ExecutionContext` vào database để tiếp tục chạy từ chunk bị ngắt.
>   2. **Quản lý Transaction phân tách (Chunk Transaction Boundary):** Tự viết thường gom tất cả vào 1 transaction lớn (dễ lock bảng, timeout) hoặc phải tự tay quản lý `TransactionTemplate`. Spring Batch tự động cô lập mỗi chunk là một transaction độc lập.
>   3. **Cơ chế chịu lỗi (Fault Tolerance):** Tự viết try-catch sẽ rất rối khi phải đếm số bản ghi lỗi, quyết định ngưỡng skip hay retry. Spring Batch chuẩn hóa qua `SkipPolicy`, `RetryPolicy` và các Listeners.
>   4. **Bộ API giám sát và Metadata tiêu chuẩn:** Spring Batch sinh sẵn bảng theo dõi lịch sử chạy, tiến độ, thời gian thực thi phục vụ dashboard quản trị.

#### ❓ Câu 2: Tiêu chí chọn kích thước Chunk (Chunk Size) là gì?
> **Trả lời:**
> - Chunk size là số lượng item được Reader đọc và Processor xử lý trước khi chuyển toàn bộ sang Writer để commit vào DB trong 1 Transaction.
> - **Nếu quá nhỏ (= 1):** Overhead mở/đóng transaction và commit I/O vào DB quá nhiều ➔ Hiệu năng giảm nghiêm trọng.
> - **Nếu quá lớn (= 50.000):** Tốn nhiều RAM, transaction kéo dài dễ gây lock bảng (deadlock / timeout DB) và nếu lỗi thì số lượng bản ghi phải rollback quá lớn.
> - **Thực tế:** Thường chọn từ 20 đến 500 tùy độ phức tạp của Processor và năng lực của DB. Trong demo này nhóm cấu hình `chunk-size: 20` để hiển thị trực quan từng commit trên log.

#### ❓ Câu 3: Làm sao đảm bảo tính toàn vẹn (Idempotency) khi một Job chạy lại hoặc được Restart?
> **Trả lời:**
> - **Tầng Ứng Dụng (Application Level):** Thay vì sinh UUID ngẫu nhiên cho bảng kết quả, chúng em sinh **ID tất định (Deterministic ID)**:
>   - Bảng bài viết trending: `id = post.getPostId()`
>   - Bảng gợi ý bạn bè: `id = UUID.nameUUIDFromBytes((userId + ":" + candidateId).getBytes())`
>   Nhờ đó, khi repository `saveAll()` chạy lại, JPA sẽ tự động cập nhật (Update) dòng hiện có thay vì chèn mới (Insert).
> - **Tầng Cơ Sở Dữ Liệu (Database Level):** Flyway migration `V4` thiết lập ràng buộc duy nhất:
>   - `uq_recommender_trending_post_id UNIQUE (post_id)`
>   - `uq_recommender_user_rec_pair UNIQUE (user_id, recommended_user_id)`
>   Đảm bảo tuyệt đối không bao giờ có 2 dòng trùng lặp trong database.

#### ❓ Câu 4: Hệ thống chống chạy trùng Job cùng một lúc như thế nào và có giới hạn gì?
> **Trả lời:**
> - **Cơ chế hiện tại:** Trước khi kích hoạt Job trong `AdminBatchController` và `BatchJobScheduler`, service gọi `jobExplorer.findRunningJobExecutions(jobName)`. Nếu có execution đang `STARTING` hoặc `STARTED`, hệ thống trả về HTTP `409 Conflict`.
> - **Giới hạn & Giải pháp tương lai:** Cách làm này kiểm tra trên database của Spring Batch nên an toàn cho mô hình **Single-Instance**. Khi mở rộng ra nhiều container song song (Multi-Instance Clustered), cần kết hợp thêm **Distributed Lock** (như ShedLock hoặc Redis lock `SETNX`) để ngăn race-condition giữa 2 node cùng gọi check tại cùng 1 mili-giây.

#### ❓ Câu 5: Dữ liệu đầu vào trong recommender-service đến từ đâu?
> **Trả lời:**
> - Trong vận hành thực tế: Dữ liệu được Kafka Consumers lắng nghe tự động từ các service khác (sự kiện tạo bài viết từ `content-service`, tương tác like/view từ `content-service`, quan hệ follow/block từ `auth-service`) và lưu vào các bảng CQRS Read-Model (`recommender_*_view`).
> - Endpoint `/api/admin/batch/demo/seed` chỉ được dùng cho môi trường demo thuyết trình để tạo tải giả lập (5.000–20.000 bản ghi) và tự động bị vô hiệu hóa trên môi trường Production (`recommender.batch.demo.enabled=false`).

#### ❓ Câu 6: Tốc độ xử lý của Job là bao nhiêu và làm sao tối ưu thêm?
> **Trả lời:**
> - Trong kịch bản demo, job xử lý 5.000 bài viết mất khoảng **7,6 – 8,2 giây** với chunk size = 20. Thời gian này là do Processor thực hiện 2 câu truy vấn SQL riêng rẽ (đếm likes và tính trung bình watch time) cho mỗi bài viết để giữ mã nguồn đơn giản, tường minh phục vụ chấm đồ án.
> - Trong môi trường Production, có thể tối ưu thời gian xử lý xuống dưới 1 giây bằng cách:
>   1. Gom nhóm số liệu tương tác qua câu lệnh SQL `GROUP BY` hoặc Database View (loại bỏ hoàn toàn vấn đề N+1 query trong Processor).
>   2. Tăng `chunk-size` lên 500–1.000 để giảm overhead đóng/mở transaction.

#### ❓ Câu 7: Vì sao `commitCount` lại là 251 thay vì 250 (khi 5.000 items chia cho chunk 20 = 250 chunks)?
> **Trả lời:**
> - Với 5.000 bản ghi và chunk size = 20, về mặt toán học có 250 chunks dữ liệu.
> - Sau khi xử lý và commit xong chunk thứ 250, Spring Batch thực hiện thêm 1 lần đọc tiếp theo để kiểm tra xem còn dữ liệu hay không (`Reader` trả về `null` báo hiệu kết thúc dữ liệu).
> - Lần đọc rỗng cuối này kích hoạt một commit để đóng transaction hoàn tất step (`empty chunk commit`) ➔ Tổng số commit được ghi nhận là 250 + 1 = **251 commits**.

#### ❓ Câu 8: Vì sao 3 bản ghi bị skip lại ứng với `rollbackCount` là 3?
> **Trả lời:**
> - Trong cơ chế Fault-Tolerant Chunk Processing của Spring Batch: khi một chunk gặp exception (ví dụ gặp 1 item `DEMO_MALFORMED`), Spring Batch không thể biết trước item nào trong chunk gây lỗi nếu chỉ commit cả mảng.
> - Vì vậy, Spring Batch chủ động **rollback toàn bộ chunk đó** (`rollbackCount++`), sau đó chuyển sang chế độ quét đơn lẻ từng item một trong transaction riêng (`item-by-item recovery`).
> - Khi quét lại từng item: item hợp lệ được commit bình thường, còn item lỗi được SkipListener bỏ qua (`skipCount++`).
> - Trong kịch bản demo có 3 bản ghi lỗi nằm ở các vị trí khác nhau trong chunk cuối, kích hoạt 3 lần rollback để quét đơn lẻ ➔ `rollbackCount = 3` tương ứng với `processSkipCount = 3`.

---

## 4. Xử Lý Sự Cố Thường Gặp Khi Demo Trực Tiếp

1. **Job bị kẹt trạng thái `STARTED` do tắt đột ngột (Ctrl+C giữa chừng):**
   - *Triệu chứng:* Khi bấm chạy lại bị báo lỗi `409 Conflict: Job hiện đang có execution đang chạy`.
   - *Cách khắc phục:* Chỉ cần khởi động lại `recommender-service`. Component `AbandonedExecutionCleaner` sẽ tự động quét và đánh dấu `FAILED` các execution mồ côi lúc khởi động, giúp mở khóa job ngay lập tức.
2. **Muốn làm sạch toàn bộ database và chạy lại từ đầu:**
   - Chạy lệnh Docker reset:
     ```bash
     docker compose -f infra/docker-compose.yml stop recommender-service postgres-recommender
     docker volume rm infra_postgres_recommender_data
     docker compose -f infra/docker-compose.yml up -d postgres-recommender
     docker compose -f infra/docker-compose.yml up -d recommender-service
     ```
   - Sau đó gọi lại Bước 1 (Seed dữ liệu) để bắt đầu kịch bản mới.
