package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.repository.DeviceTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDeliveryService {

    private static final String PRESENCE_KEY_PREFIX = "presence:";

    private final StringRedisTemplate redisTemplate;
    private final WebSocketNotificationService webSocketNotificationService;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ObjectMapper objectMapper;

    public void deliver(Notification notification) {
        deliver(notification, true);
    }

    public void deliverGroupedUpdate(Notification notification) {
        deliver(notification, false);
    }

    private void deliver(Notification notification, boolean offlineFallback) {
        UUID userId = notification.getUserId();
        boolean isOnline = Boolean.TRUE.equals(redisTemplate.hasKey(PRESENCE_KEY_PREFIX + userId));

        // A presence key without an open session means the connection died before the close
        // handler could clean up (e.g. instance crash). Treat it as offline instead of
        // silently dropping the push.
        if (isOnline && !webSocketNotificationService.hasActiveSession(userId)) {
            log.warn("Stale presence for user {}: key exists but no open WebSocket session; clearing presence and falling back to push", userId);
            try {
                redisTemplate.delete(PRESENCE_KEY_PREFIX + userId);
            } catch (Exception e) {
                log.warn("Failed to clear stale presence for user {}: {}", userId, e.getMessage());
            }
            isOnline = false;
        }

        if (isOnline) {
            try {
                Map<String, Object> payload = buildWsPayload(notification, userId);
                webSocketNotificationService.sendToUser(userId, payload);
                log.info("Sent WS notification to online user {}", userId);
            } catch (Exception e) {
                log.warn("WS delivery failed for user {}", userId, e);
                if (offlineFallback) {
                    sendMockFcm(userId, notification);
                } else {
                    throw e;
                }
            }
            return;
        }

        if (offlineFallback) {
            sendMockFcm(userId, notification);
        }
    }

    /**
     * Builds the WebSocket payload contract.
     *
     * <p>{@code payload} is the parsed JSON object from the notification row (not the raw
     * string), with the raw string used only as a fallback when the row does not contain
     * valid JSON. {@code messageText} keeps the human-readable message for backwards
     * compatibility with clients listening on the old {@code message} shape.
     */
    private Map<String, Object> buildWsPayload(Notification notification, UUID userId) {
        String rawPayload = notification.getPayload();
        Object parsedPayload;
        String messageText = "";

        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            if (node == null || node.isNull()) {
                parsedPayload = rawPayload == null ? "" : rawPayload;
            } else {
                parsedPayload = node;
                JsonNode message = node.get("message");
                if (message != null && !message.isNull()) {
                    messageText = message.isTextual() ? message.asText() : message.toString();
                }
            }
        } catch (Exception e) {
            log.warn("Notification {} payload is not valid JSON; sending raw string on WebSocket: {}", notification.getId(), e.getMessage());
            parsedPayload = rawPayload == null ? "" : rawPayload;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notification.getId().toString());
        payload.put("userId", userId.toString());
        payload.put("type", notification.getType().name());
        payload.put("payload", parsedPayload);
        payload.put("messageText", messageText);
        payload.put("createdAt", notification.getCreatedAt() != null ? notification.getCreatedAt().toString() : "");
        return payload;
    }

    public void sendMockFcm(UUID userId, Notification notification) {
        long deviceCount = deviceTokenRepository.findByUserId(userId).size();
        log.info("Mock FCM push sent to user {} for notification {} (deviceCount={})", userId, notification.getId(), deviceCount);
    }
}
