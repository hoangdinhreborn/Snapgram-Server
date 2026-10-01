package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationOutboxStatus;
import com.example.notification.entity.NotificationPushOutbox;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.CallEvent;
import com.example.notification.event.ChatMessageEvent;
import com.example.notification.event.ContentInteractionEvent;
import com.example.notification.event.FollowEvent;
import com.example.notification.event.MentionEvent;
import com.example.notification.event.ModerationEvent;
import com.example.notification.event.PostCreatedEvent;
import com.example.notification.event.StoryCreatedEvent;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.repository.NotificationPushOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumerService {

    private final NotificationRepository notificationRepository;
    private final NotificationPushOutboxRepository pushOutboxRepository;
    private final ObjectMapper objectMapper;
    private final NotificationEventProcessingService eventProcessingService;

    @KafkaListener(topics = "content.interaction", groupId = "notification-service")
    public void consumeInteractionEvent(ConsumerRecord<String, String> record) {
        receive(record, ContentInteractionEvent.class, event -> {
            handleInteractionEvent(event);
        });
    }

    @KafkaListener(topics = "content.post-created", groupId = "notification-service")
    public void consumePostCreatedEvent(ConsumerRecord<String, String> record) {
        receive(record, PostCreatedEvent.class, this::handlePostCreatedEvent);
    }

    @KafkaListener(topics = "content.story-created", groupId = "notification-service")
    public void consumeStoryCreatedEvent(ConsumerRecord<String, String> record) {
        receive(record, StoryCreatedEvent.class, this::handleStoryCreatedEvent);
    }

    @KafkaListener(topics = "mention.events", groupId = "notification-service")
    public void consumeMentionEvent(ConsumerRecord<String, String> record) {
        receive(record, MentionEvent.class, this::handleMentionEvent);
    }

    @KafkaListener(topics = "follow.events", groupId = "notification-service")
    public void consumeFollowEvent(ConsumerRecord<String, String> record) {
        receive(record, FollowEvent.class, this::handleFollowEvent);
    }

    @KafkaListener(topics = "chat.messages", groupId = "notification-service")
    public void consumeChatMessageEvent(ConsumerRecord<String, String> record) {
        receive(record, ChatMessageEvent.class, this::handleChatMessageEvent);
    }

    @KafkaListener(topics = "moderation.events", groupId = "notification-service")
    public void consumeModerationEvent(ConsumerRecord<String, String> record) {
        receive(record, ModerationEvent.class, this::handleModerationEvent);
    }

    @KafkaListener(topics = "call.events", groupId = "notification-service")
    public void consumeCallEvent(ConsumerRecord<String, String> record) {
        receive(record, CallEvent.class, this::handleCallEvent);
    }

    private <T> void receive(ConsumerRecord<String, String> record, Class<T> eventType, Consumer<T> handler) {
        JsonNode root;
        T event;
        try {
            root = objectMapper.readTree(record.value());
            JsonNode payload = root.has("data") ? root.get("data") : root;
            event = objectMapper.treeToValue(payload, eventType);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON event for topic " + record.topic(), e);
        }
        if (event == null) {
            throw new IllegalArgumentException("Null event for topic " + record.topic());
        }

        UUID eventId = extractEventId(root, record);
        eventProcessingService.process(record.topic(), eventId, () -> handler.accept(event));
    }

    private UUID extractEventId(JsonNode root, ConsumerRecord<String, String> record) {
        JsonNode eventIdNode = root.get("eventId");
        if (eventIdNode != null && !eventIdNode.isNull() && !eventIdNode.asText().isBlank()) {
            return UUID.fromString(eventIdNode.asText());
        }

        String recordIdentity = record.topic() + ":" + record.partition() + ":" + record.offset();
        log.warn("Event on topic {} has no eventId; using Kafka record identity until producer contract is updated", record.topic());
        return UUID.nameUUIDFromBytes(recordIdentity.getBytes(StandardCharsets.UTF_8));
    }

    public void handleLikeEvent(ContentInteractionEvent event) {
        handleInteractionEvent(event);
    }

    public void handleInteractionEvent(ContentInteractionEvent event) {
        if (event.getType() == null || event.getUserId() == null
                || event.getTargetUserId() == null || event.getPostId() == null) {
            throw new IllegalArgumentException("Interaction event is missing required fields");
        }

        UUID actorId = UUID.fromString(event.getUserId());
        UUID userId = UUID.fromString(event.getTargetUserId());
        UUID postId = UUID.fromString(event.getPostId());
        NotificationType type;
        try {
            type = NotificationType.valueOf(event.getType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Skipping unsupported interaction notification type: {}", event.getType());
            return;
        }
        if (type != NotificationType.LIKE && type != NotificationType.COMMENT) {
            return;
        }

        Instant now = Instant.now();
        Notification notification = notificationRepository
            .findRecentUnreadGroup(
                userId, type, "POST", postId.toString(), now.minus(1, ChronoUnit.HOURS), PageRequest.of(0, 1))
            .stream()
            .findFirst()
            .orElse(null);

        if (notification == null) {
            notification = new Notification();
            notification.setUserId(userId);
            notification.setActorId(actorId);
            notification.setType(type);
            notification.setTargetType("POST");
            notification.setTargetId(postId.toString());
            notification.setPayload(serializePayload(Map.of(
                    "postId", postId.toString(),
                    "actorIds", List.of(actorId.toString()),
                    "count", 1,
                    "message", interactionMessage(type, 1),
                    "createdAt", event.getCreatedAt() == null ? now.toString() : event.getCreatedAt().toString()
            )));
            notification.setIsRead(false);
            saveAndQueue(notification, false);
            return;
        }

        try {
            JsonNode currentPayload = objectMapper.readTree(notification.getPayload());
            ObjectNode payload = currentPayload instanceof ObjectNode objectNode
                    ? objectNode
                    : objectMapper.createObjectNode();
            JsonNode currentActorIds = payload.get("actorIds");
            ArrayNode actorIds = currentActorIds instanceof ArrayNode arrayNode
                    ? arrayNode
                    : payload.putArray("actorIds");
            if (actorIds.isEmpty() && notification.getActorId() != null) {
                actorIds.add(notification.getActorId().toString());
            }
            for (JsonNode currentActorId : actorIds) {
                if (actorId.toString().equals(currentActorId.asText())) {
                    return;
                }
            }

            actorIds.add(actorId.toString());
            payload.put("postId", postId.toString());
            payload.put("count", actorIds.size());
            payload.put("message", interactionMessage(type, actorIds.size()));
            notification.setActorId(actorId);
            notification.setPayload(objectMapper.writeValueAsString(payload));
            saveAndQueue(notification, true);
        } catch (JsonProcessingException e) {
            log.error("Failed to update grouped interaction notification", e);
        }
    }

    public void handlePostCreatedEvent(PostCreatedEvent event) {
        if (event.getAuthorId() == null || event.getPostId() == null || event.getFollowerIds() == null) {
            throw new IllegalArgumentException("Post-created event is missing authorId, postId, or followerIds");
        }

        UUID authorId = UUID.fromString(event.getAuthorId());
        UUID postId = UUID.fromString(event.getPostId());

        for (String followerIdStr : event.getFollowerIds()) {
            try {
                UUID followerId = UUID.fromString(followerIdStr);

                String payload = """
                    {
                      "postId": "%s",
                      "authorId": "%s",
                      "message": "đã đăng một bài viết mới"
                    }
                    """.formatted(postId, authorId);

                Notification notification = new Notification();
                notification.setUserId(followerId);
                notification.setActorId(authorId);
                notification.setType(NotificationType.POST_CREATED);
                notification.setTargetType("POST");
                notification.setTargetId(postId.toString());
                notification.setPayload(payload);
                notification.setIsRead(false);

                saveAndQueue(notification, false);
                log.info("Saved POST_CREATED notification for follower {} from author {}", followerId, authorId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid followerId in post-created event: " + followerIdStr, e);
            }
        }
    }

    public void handleStoryCreatedEvent(StoryCreatedEvent event) {
        if (event.getAuthorId() == null || event.getStoryId() == null || event.getFollowerIds() == null) {
            throw new IllegalArgumentException("Story-created event is missing authorId, storyId, or followerIds");
        }

        UUID authorId = UUID.fromString(event.getAuthorId());
        UUID storyId = UUID.fromString(event.getStoryId());

        for (String followerIdStr : event.getFollowerIds()) {
            try {
                UUID followerId = UUID.fromString(followerIdStr);
                String payload = objectMapper.writeValueAsString(Map.of(
                        "storyId", storyId.toString(),
                        "authorId", authorId.toString(),
                        "mediaUrl", event.getMediaUrl() == null ? "" : event.getMediaUrl(),
                        "message", "đã đăng một tin mới"
                ));

                Notification notification = new Notification();
                notification.setUserId(followerId);
                notification.setActorId(authorId);
                notification.setType(NotificationType.STORY_CREATED);
                notification.setTargetType("STORY");
                notification.setTargetId(storyId.toString());
                notification.setPayload(payload);
                notification.setIsRead(false);

                saveAndQueue(notification, false);
                log.info("Saved STORY_CREATED notification for follower {} from author {}", followerId, authorId);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid followerId in story-created event: " + followerIdStr, e);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize story-created payload", e);
            }
        }
    }

    public void handleMentionEvent(MentionEvent event) {
        if (event.getMentionedUserId() == null || event.getPostId() == null || event.getAuthorId() == null) {
            throw new IllegalArgumentException("Mention event is missing required fields");
        }

        UUID userId = UUID.fromString(event.getMentionedUserId());
        UUID authorId = UUID.fromString(event.getAuthorId());
        UUID postId = UUID.fromString(event.getPostId());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "postId", postId.toString(),
                    "authorId", authorId.toString(),
                    "message", "đã nhắc đến bạn trong một bài viết",
                    "createdAt", event.getCreatedAt() == null
                            ? Instant.now().toString()
                            : event.getCreatedAt().toString()
            ));
        } catch (JsonProcessingException e) {
            payload = "{}";
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActorId(authorId);
        notification.setType(NotificationType.MENTION);
        notification.setTargetType("POST");
        notification.setTargetId(postId.toString());
        notification.setPayload(payload);
        notification.setIsRead(false);

        saveAndQueue(notification, false);
        log.info("Saved MENTION notification for user {} from author {}", userId, authorId);
    }

    public void handleFollowEvent(FollowEvent event) {
        if (event.getFollowerId() == null || event.getFollowingId() == null || event.getStatus() == null) {
            throw new IllegalArgumentException("Follow event is missing required fields");
        }
        if (!"ACCEPTED".equalsIgnoreCase(event.getStatus())) {
            return;
        }

        UUID followerId = UUID.fromString(event.getFollowerId());
        UUID followingId = UUID.fromString(event.getFollowingId());
        createAndDeliver(followingId, followerId, NotificationType.FOLLOW, "USER", followerId.toString(), Map.of(
                "followerId", followerId.toString(),
                "message", "đã bắt đầu theo dõi bạn",
                "createdAt", event.getCreatedAt() == null ? Instant.now().toString() : event.getCreatedAt().toString()
        ));
    }

    public void handleChatMessageEvent(ChatMessageEvent event) {
        if (event.getSenderId() == null || event.getReceiverId() == null || event.getConversationId() == null) {
            throw new IllegalArgumentException("Chat message event is missing required fields");
        }
        if (event.getMuted() == null) {
            log.warn("Skipping chat message because recipient mute state is missing");
            return;
        }
        if (event.getMuted()) {
            return;
        }

        UUID senderId = UUID.fromString(event.getSenderId());
        UUID receiverId = UUID.fromString(event.getReceiverId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", event.getConversationId());
        payload.put("messageId", event.getMessageId());
        payload.put("message", "đã gửi cho bạn một tin nhắn");
        payload.put("createdAt", event.getCreatedAt() == null ? Instant.now().toString() : event.getCreatedAt().toString());
        createAndDeliver(receiverId, senderId, NotificationType.MESSAGE, "CONVERSATION", event.getConversationId(), payload);
    }

    public void handleModerationEvent(ModerationEvent event) {
        if (event.getStatus() == null || !"RESOLVED".equalsIgnoreCase(event.getStatus())) {
            return;
        }
        if (event.getReporterId() == null || event.getReportId() == null) {
            throw new IllegalArgumentException("Resolved moderation event is missing reporterId or reportId");
        }

        UUID reporterId = UUID.fromString(event.getReporterId());
        UUID moderatorId = event.getModeratorId() == null ? null : UUID.fromString(event.getModeratorId());
        createAndDeliver(reporterId, moderatorId, NotificationType.REPORT_RESOLVED, "REPORT", event.getReportId(), Map.of(
                "reportId", event.getReportId(),
                "status", "RESOLVED",
                "message", "báo cáo của bạn đã được xử lý",
                "createdAt", event.getCreatedAt() == null ? Instant.now().toString() : event.getCreatedAt().toString()
        ));
    }

    public void handleCallEvent(CallEvent event) {
        if (!Boolean.TRUE.equals(event.getMissed())) {
            return;
        }
        if (event.getCallerId() == null || event.getCalleeId() == null || event.getCallId() == null) {
            throw new IllegalArgumentException("Missed call event is missing required fields");
        }

        UUID callerId = UUID.fromString(event.getCallerId());
        UUID calleeId = UUID.fromString(event.getCalleeId());
        createAndDeliver(calleeId, callerId, NotificationType.MISSED_CALL, "CALL", event.getCallId(), Map.of(
                "callId", event.getCallId(),
                "callerId", callerId.toString(),
                "message", "bạn đã bỏ lỡ một cuộc gọi",
                "createdAt", event.getCreatedAt() == null ? Instant.now().toString() : event.getCreatedAt().toString()
        ));
    }

    private void createAndDeliver(UUID userId, UUID actorId, NotificationType type,
                                  String targetType, String targetId, Map<String, Object> payload) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActorId(actorId);
        notification.setType(type);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setPayload(serializePayload(payload));
        notification.setIsRead(false);
        saveAndQueue(notification, false);
    }

    private void saveAndQueue(Notification notification, boolean groupedUpdate) {
        Notification savedNotification = notificationRepository.save(notification);
        NotificationPushOutbox outbox = new NotificationPushOutbox();
        outbox.setNotificationId(savedNotification.getId());
        outbox.setStatus(NotificationOutboxStatus.PENDING);
        outbox.setGroupedUpdate(groupedUpdate);
        outbox.setAttempts(0);
        pushOutboxRepository.save(outbox);
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification payload", e);
            return "{}";
        }
    }

    private String interactionMessage(NotificationType type, int count) {
        String action = type == NotificationType.LIKE ? "thích" : "bình luận";
        return count == 1
                ? "đã " + action + " bài viết của bạn"
                : count + " người đã " + action + " bài viết của bạn";
    }
}
