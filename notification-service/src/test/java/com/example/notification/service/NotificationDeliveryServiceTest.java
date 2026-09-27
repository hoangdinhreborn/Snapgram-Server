package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.DeviceTokenRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    void shouldSendWsWhenUserOnline() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.LIKE);
        notification.setPayload("{\"postId\":\"abc\"}");
        notification.setCreatedAt(Instant.now());

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);

        notificationDeliveryService.deliver(notification);

        verify(webSocketNotificationService, times(1)).sendToUser(eq(userId), anyMap());
    }

    @Test
    void shouldSendMockFcmWhenUserOffline() {
        UUID userId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.LIKE);
        notification.setPayload("{\"postId\":\"abc\"}");
        notification.setCreatedAt(Instant.now());

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.FALSE);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of());

        notificationDeliveryService.deliver(notification);

        verify(webSocketNotificationService, never()).sendToUser(any(), anyMap());
    }
}
