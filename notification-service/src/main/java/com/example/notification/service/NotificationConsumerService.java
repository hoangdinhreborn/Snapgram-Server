package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.event.ContentInteractionEvent;
import com.example.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumerService {

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryService notificationDeliveryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "content.interaction", groupId = "notification-service")
    public void consumeInteractionEvent(String message) {
        try {
            ContentInteractionEvent event = objectMapper.readValue(message, ContentInteractionEvent.class);
            if (event == null || event.getType() == null) {
                return;
            }

            if ("LIKE".equalsIgnoreCase(event.getType())) {
                handleLikeEvent(event);
            }
        } catch (Exception e) {
            log.error("Failed to process interaction event: {}", message, e);
        }
    }

    public void handleLikeEvent(ContentInteractionEvent event) {
        if (event.getUserId() == null || event.getTargetUserId() == null) {
            return;
        }

        UUID actorId = UUID.fromString(event.getUserId());
        UUID userId = UUID.fromString(event.getTargetUserId());
        UUID postId = UUID.fromString(event.getPostId());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "postId", postId.toString(),
                    "actorId", actorId.toString(),
                    "actorDisplayName", "User-" + actorId.toString().substring(0, 8),
                    "message", "đã thích bài viết của bạn",
                    "createdAt", Instant.now().toString()
            ));
        } catch (JsonProcessingException e) {
            payload = "{}";
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setActorId(actorId);
        notification.setType(NotificationType.LIKE);
        notification.setTargetType("POST");
        notification.setTargetId(postId.toString());
        notification.setPayload(payload);
        notification.setIsRead(false);

        Notification savedNotification = notificationRepository.save(notification);
        notificationDeliveryService.deliver(savedNotification);
        log.info("Saved LIKE notification for user {} from actor {}", userId, actorId);
    }
}
