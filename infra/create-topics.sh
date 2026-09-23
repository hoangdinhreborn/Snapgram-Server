#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-snapgram-kafka}"

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
)

echo "Waiting for Kafka at ${BOOTSTRAP_SERVER}..."
until docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" >/dev/null 2>&1; do
  sleep 2
done

for entry in "${TOPICS[@]}"; do
  topic="${entry%%:*}"
  partitions="${entry##*:}"
  docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "$BOOTSTRAP_SERVER" \
    --create --if-not-exists \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 >/dev/null
  echo "OK: $topic (${partitions} partitions)"
done

echo
echo "Topics:"
docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "$BOOTSTRAP_SERVER" --list | sort
