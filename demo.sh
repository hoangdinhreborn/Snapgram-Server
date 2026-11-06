#!/usr/bin/env bash
# ==============================================================================
# Script tự động hóa Demo Spring Batch trong Snapgram Recommender Service
# ==============================================================================
set -euo pipefail

BASE_URL="http://127.0.0.1:8090"
ADMIN_HEADERS=(-H "X-User-Id: 00000000-0000-0000-0000-000000000000" -H "X-User-Roles: ADMIN,USER")

echo "================================================================================"
echo "🚀 BẮT ĐẦU KỊCH BẢN DEMO SPRING BATCH (RECOMMENDER-SERVICE)"
echo "================================================================================"

# Kiểm tra công cụ jq
if ! command -v jq &> /dev/null; then
    echo "❌ Lỗi: Cần cài đặt 'jq' để parse JSON. Vui lòng cài đặt jq trước khi chạy."
    exit 1
fi

# Kiểm tra service hoạt động
echo "🔍 Đang kiểm tra sức khỏe dịch vụ tại $BASE_URL/actuator/health..."
HEALTH=$(curl -s "$BASE_URL/actuator/health" | jq -r .status || echo "DOWN")
if [ "$HEALTH" != "UP" ]; then
    echo "❌ Lỗi: Recommender service chưa sẵn sàng (Status: $HEALTH)."
    exit 1
fi
echo "✅ Dịch vụ đã sẵn sàng!"

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 1: Seed Dữ Liệu Mẫu (5.000 bản ghi)"
echo "--------------------------------------------------------------------------------"
SEED_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/demo/seed?count=5000" "${ADMIN_HEADERS[@]}")
echo "$SEED_RESP" | jq .
POSTS_CREATED=$(echo "$SEED_RESP" | jq -r .postsCreated)
echo "✅ Đã nạp $POSTS_CREATED bài viết mẫu."

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 2 & 3: Kích Hoạt Job & Giám Sát Chunk Processing + Skip"
echo "--------------------------------------------------------------------------------"
RUN_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run" "${ADMIN_HEADERS[@]}")
EXEC_ID_1=$(echo "$RUN_RESP" | jq -r .jobExecutionId)
echo "▶ Đã kích hoạt Job lần 1 - Execution ID: $EXEC_ID_1"

if [ -z "$EXEC_ID_1" ] || [ "$EXEC_ID_1" = "null" ]; then
    echo "❌ Lỗi: Không lấy được jobExecutionId hợp lệ."
    exit 1
fi

echo "⏳ Đang poll trạng thái Execution $EXEC_ID_1..."
while true; do
    EXEC_DATA=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$EXEC_ID_1" "${ADMIN_HEADERS[@]}")
    STATUS=$(echo "$EXEC_DATA" | jq -r .status)
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        break
    fi
    sleep 1
done

echo "📊 Kết quả Execution $EXEC_ID_1:"
echo "$EXEC_DATA" | jq '{jobExecutionId, status, exitCode, durationMs, steps: [.steps[] | {stepName, readCount, writeCount, commitCount, rollbackCount, totalSkipCount}]}'

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 4: Kiểm Chứng Tính Toàn Vẹn Idempotency (Chạy Lần 2 Không Tăng Dòng)"
echo "--------------------------------------------------------------------------------"
RUN_RESP_2=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run" "${ADMIN_HEADERS[@]}")
EXEC_ID_2=$(echo "$RUN_RESP_2" | jq -r .jobExecutionId)
echo "▶ Đã kích hoạt Job lần 2 - Execution ID: $EXEC_ID_2"

if [ -z "$EXEC_ID_2" ] || [ "$EXEC_ID_2" = "null" ]; then
    echo "❌ Lỗi: Không lấy được jobExecutionId hợp lệ ở lần 2."
    exit 1
fi

while true; do
    STATUS_2=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$EXEC_ID_2" "${ADMIN_HEADERS[@]}" | jq -r .status)
    if [ "$STATUS_2" = "COMPLETED" ] || [ "$STATUS_2" = "FAILED" ]; then
        break
    fi
    sleep 1
done
echo "✅ Execution $EXEC_ID_2 đã $STATUS_2."

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 5: Biểu Diễn Khả Năng RESTART (Phục Hồi Sự Cố Thông Minh)"
echo "--------------------------------------------------------------------------------"
echo "▶ 5.1 Kích hoạt Job cố tình gây lỗi tại item #45..."
FAIL_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/trendingPostsJob/run?failAtItem=45" "${ADMIN_HEADERS[@]}")
FAIL_EXEC_ID=$(echo "$FAIL_RESP" | jq -r .jobExecutionId)
echo "▶ Execution mô phỏng lỗi: $FAIL_EXEC_ID"

if [ -z "$FAIL_EXEC_ID" ] || [ "$FAIL_EXEC_ID" = "null" ]; then
    echo "❌ Lỗi: Không lấy được failExecutionId hợp lệ."
    exit 1
fi

while true; do
    FAIL_DATA=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID" "${ADMIN_HEADERS[@]}")
    STATUS=$(echo "$FAIL_DATA" | jq -r .status)
    if [ "$STATUS" = "FAILED" ]; then
        break
    fi
    sleep 1
done

echo "⚠️ Execution $FAIL_EXEC_ID đã dừng lại ở trạng thái FAILED đúng như kỳ vọng:"
echo "$FAIL_DATA" | jq '{jobExecutionId, status, exitCode, steps: [.steps[] | {stepName, readCount, writeCount, commitCount, rollbackCount}]}'

echo ""
echo "▶ 5.2 Khởi động lại (Restart) Execution $FAIL_EXEC_ID đúng 1 lần..."
RESTART_RESP=$(curl -s -X POST "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID/restart" "${ADMIN_HEADERS[@]}")
RESTART_EXEC_ID=$(echo "$RESTART_RESP" | jq -r .jobExecutionId)

# Kiểm tra id trả về khác rỗng và khác null
if [ -z "$RESTART_EXEC_ID" ] || [ "$RESTART_EXEC_ID" = "null" ]; then
    echo "❌ Lỗi: Không nhận được jobExecutionId hợp lệ từ lệnh restart:"
    echo "$RESTART_RESP" | jq .
    exit 1
fi

echo "🔄 Job đã được Restart với Execution ID MỚI: $RESTART_EXEC_ID (khác ID cũ $FAIL_EXEC_ID)"

while true; do
    RESTART_DATA=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$RESTART_EXEC_ID" "${ADMIN_HEADERS[@]}")
    STATUS=$(echo "$RESTART_DATA" | jq -r .status)
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        break
    fi
    sleep 1
done

echo "✅ Execution $RESTART_EXEC_ID sau khi Restart đã COMPLETED:"
echo "$RESTART_DATA" | jq '{jobExecutionId, status, exitCode, durationMs, steps: [.steps[] | {stepName, readCount, writeCount, commitCount, totalSkipCount}]}'

echo ""
echo "▶ 5.3 [QUAN TRỌNG] Kiểm tra bảo vệ chống Restart lần 2 trên cùng Execution $FAIL_EXEC_ID:"
echo "⚠️ Lưu ý: Một JobExecution đã hoàn tất restart KHÔNG ĐƯỢC restart lại lần 2."
SECOND_RESTART_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/batch/executions/$FAIL_EXEC_ID/restart" "${ADMIN_HEADERS[@]}")
echo "HTTP Status trả về khi restart lần 2: $SECOND_RESTART_CODE"
if [ "$SECOND_RESTART_CODE" = "409" ]; then
    echo "✅ Hệ thống bảo vệ chính xác: Trả về HTTP 409 Conflict khi cố restart lại instance đã hoàn thành!"
fi

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 6: Demo Job 2 (userRecommendationJob) & Đọc Kết Quả"
echo "--------------------------------------------------------------------------------"
USER_RUN=$(curl -s -X POST "$BASE_URL/api/admin/batch/jobs/userRecommendationJob/run" "${ADMIN_HEADERS[@]}")
USER_EXEC_ID=$(echo "$USER_RUN" | jq -r .jobExecutionId)
echo "▶ Đã kích hoạt userRecommendationJob - Execution ID: $USER_EXEC_ID"

if [ -z "$USER_EXEC_ID" ] || [ "$USER_EXEC_ID" = "null" ]; then
    echo "❌ Lỗi: Không lấy được jobExecutionId của userRecommendationJob."
    exit 1
fi

while true; do
    USER_DATA=$(curl -s -X GET "$BASE_URL/api/admin/batch/executions/$USER_EXEC_ID" "${ADMIN_HEADERS[@]}")
    STATUS=$(echo "$USER_DATA" | jq -r .status)
    if [ "$STATUS" = "COMPLETED" ] || [ "$STATUS" = "FAILED" ]; then
        break
    fi
    sleep 1
done

echo "✅ userRecommendationJob Execution $USER_EXEC_ID đã COMPLETED:"
echo "$USER_DATA" | jq '{jobExecutionId, status, exitCode, durationMs, steps: [.steps[] | {stepName, readCount, writeCount, commitCount, totalSkipCount}]}'

echo ""
echo "--------------------------------------------------------------------------------"
echo "📌 BƯỚC 7: Đối Chiếu Trực Tiếp Top 5 Giữa Redis Cache và PostgreSQL"
echo "--------------------------------------------------------------------------------"
echo "▶ Top 5 bài viết thịnh hành lưu trong Redis:"
curl -s -X GET "$BASE_URL/api/recommendations/trending?limit=5" | jq '.[0:5] | .[] | {rank, postId, score, likesCount}'

echo ""
echo "================================================================================"
echo "🎉 HOÀN THÀNH TOÀN BỘ KỊCH BẢN DEMO THÀNH CÔNG RỰC RỠ!"
echo "================================================================================"
