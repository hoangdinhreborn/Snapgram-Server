#!/usr/bin/env bash
# ============================================================
# Create Kafka Topics for Snapgram
# ============================================================
set -e

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-snapgram-kafka}"
KAFKA_DIRECT="${KAFKA_DIRECT:-false}"
MAX_RETRIES="${MAX_RETRIES:-30}"
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
  "payment.completed:3"
  "content.post-created.DLT:3"
  "content.story-created.DLT:3"
  "content.interaction.DLT:3"
  "follow.events.DLT:3"
  "mention.events.DLT:3"
  "chat.messages.DLT:6"
  "moderation.events.DLT:3"
  "call.events.DLT:3"
  "payment.completed.DLT:3"
  "media.uploaded:3"
)

kafka_cmd() {
  if [ "$KAFKA_DIRECT" = "true" ]; then
    /opt/kafka/bin/"$@"
  else
    docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/"$@"
  fi
}

echo "Waiting for Kafka at ${BOOTSTRAP_SERVER}..."
retries=0
until kafka_cmd kafka-broker-api-versions.sh --bootstrap-server "$BOOTSTRAP_SERVER" >/dev/null 2>&1; do
  retries=$((retries + 1))
  if [ "$retries" -ge "$MAX_RETRIES" ]; then
    echo "Kafka not reachable after $((MAX_RETRIES * RETRY_INTERVAL))s - aborting."
    exit 1
  fi
  sleep "$RETRY_INTERVAL"
done
echo "Kafka is ready."

echo "Creating topics..."
for entry in "${TOPICS[@]}"; do
  topic="${entry%%:*}"
  partitions="${entry##*:}"
  kafka_cmd kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 >/dev/null 2>&1 || true
  echo "  Created/verified: $topic ($partitions partitions)"
done

echo "All Kafka topics:"
kafka_cmd kafka-topics.sh --bootstrap-server "$BOOTSTRAP_SERVER" --list | sort | sed 's/^/   /'
echo "Done."
