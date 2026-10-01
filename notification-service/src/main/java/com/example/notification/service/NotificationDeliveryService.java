package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    public void deliver(Notification notification) {
        deliver(notification, true);
    }

    public void deliverGroupedUpdate(Notification notification) {
        deliver(notification, false);
    }

    private void deliver(Notification notification, boolean offlineFallback) {
        UUID userId = notification.getUserId();
        boolean isOnline = Boolean.TRUE.equals(redisTemplate.hasKey(PRESENCE_KEY_PREFIX + userId));

        if (isOnline) {
            try {
                Map<String, Object> payload = Map.of(
                        "notificationId", notification.getId().toString(),
                        "userId", userId.toString(),
                        "type", notification.getType().name(),
                        "message", notification.getPayload(),
                        "createdAt", notification.getCreatedAt().toString()
                );
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

    public void sendMockFcm(UUID userId, Notification notification) {
        long deviceCount = deviceTokenRepository.findByUserId(userId).size();
        log.info("Mock FCM push sent to user {} for notification {} (deviceCount={})", userId, notification.getId(), deviceCount);
    }
}
