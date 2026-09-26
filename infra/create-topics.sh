#!/usr/bin/env bash
# ============================================================
# Create Kafka Topics for Snapgram
# Usage:
#   ./create-topics.sh                      # dùng docker exec (default)
#   KAFKA_DIRECT=true ./create-topics.sh    # kết nối trực tiếp không qua docker
# ============================================================
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-snapgram-kafka}"
KAFKA_DIRECT="${KAFKA_DIRECT:-false}"      # set true nếu Kafka accessible trực tiếp
MAX_RETRIES="${MAX_RETRIES:-30}"           # tối đa 30 * 2s = 60 giây
RETRY_INTERVAL="${RETRY_INTERVAL:-2}"

TOPICS=(
  "user.events:3"
  "content.post-created:3"
  "content.post-updated:3"
  "content.interaction:3"
  "content.story-created:3"
  "follow.events:3"
  "chat.messages:6"
  "chat.escalation:3"
  "chat.state-changed:3"
  "staff.presence:3"
  "call.events:3"
  "mention.events:3"
  "moderation.events:3"
  "notification.events:3"
  "media.uploaded:3"
)

# ── Helper: run kafka CLI (inside container OR directly) ──────────────────────
kafka_cmd() {
  if [[ "$KAFKA_DIRECT" == "true" ]]; then
    /opt/kafka/bin/"$@"
  else
    docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/"$@"
  fi
}

# ── Wait for Kafka to be ready ────────────────────────────────────────────────
echo "⏳ Waiting for Kafka at ${BOOTSTRAP_SERVER} (max ${MAX_RETRIES} retries)..."
retries=0
until kafka_cmd kafka-broker-api-versions.sh \
        --bootstrap-server "$BOOTSTRAP_SERVER" >/dev/null 2>&1; do
  retries=$((retries + 1))
  if [[ $retries -ge $MAX_RETRIES ]]; then
    echo "❌ Kafka not reachable after $((MAX_RETRIES * RETRY_INTERVAL))s — aborting."
    echo "   Check that container '${KAFKA_CONTAINER}' is running: docker ps | grep kafka"
    exit 1
  fi
  echo "   [${retries}/${MAX_RETRIES}] Kafka not ready yet, retrying in ${RETRY_INTERVAL}s..."
  sleep "$RETRY_INTERVAL"
done
echo "✅ Kafka is ready."
echo ""

# ── Create topics ─────────────────────────────────────────────────────────────
echo "📦 Creating topics..."
for entry in "${TOPICS[@]}"; do
  topic="${entry%%:*}"
  partitions="${entry##*:}"
  kafka_cmd kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 >/dev/null
  echo "  ✔ $topic ($partitions partitions)"
done

# ── Print final list ──────────────────────────────────────────────────────────
echo ""
echo "📋 All Kafka topics:"
kafka_cmd kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" \
  --list | sort | sed 's/^/   /'

echo ""
echo "🎉 Done."
