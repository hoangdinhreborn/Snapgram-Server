package com.example.notification.service;

import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.DeviceTokenRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WebSocketNotificationService webSocketNotificationService;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    void shouldSendWsWhenUserOnline() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);
        when(webSocketNotificationService.hasActiveSession(userId)).thenReturn(true);

        notificationDeliveryService.deliver(notification);

        verify(webSocketNotificationService, times(1)).sendToUser(eq(userId), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendParsedPayloadObjectAndMessageTextOverWs() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);
        notification.setPayload("{\"postId\":\"post-1\",\"message\":\"alice liked your post\"}");

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);
        when(webSocketNotificationService.hasActiveSession(userId)).thenReturn(true);

        notificationDeliveryService.deliver(notification);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService).sendToUser(eq(userId), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload)
                .containsKeys("notificationId", "userId", "type", "payload", "messageText", "createdAt")
                .doesNotContainKey("message");
        assertThat(payload.get("notificationId")).isEqualTo(notification.getId().toString());
        assertThat(payload.get("userId")).isEqualTo(userId.toString());
        assertThat(payload.get("type")).isEqualTo(NotificationType.LIKE.name());

        // The wire contract requires an embedded JSON object, not the raw payload string.
        assertThat(payload.get("payload")).isInstanceOf(JsonNode.class);
        JsonNode parsed = (JsonNode) payload.get("payload");
        assertThat(parsed.isObject()).isTrue();
        assertThat(parsed.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("messageText")).isEqualTo("alice liked your post");
        assertThat(payload.get("createdAt")).isEqualTo(notification.getCreatedAt().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFallbackToRawPayloadStringWhenJsonIsInvalid() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);
        notification.setPayload("not-a-json-payload");

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);
        when(webSocketNotificationService.hasActiveSession(userId)).thenReturn(true);

        notificationDeliveryService.deliver(notification);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(webSocketNotificationService).sendToUser(eq(userId), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("payload")).isEqualTo("not-a-json-payload");
        assertThat(payload.get("messageText")).isEqualTo("");
    }

    @Test
    void shouldSendMockFcmWhenUserOffline() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.FALSE);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of());

        notificationDeliveryService.deliver(notification);

        verify(webSocketNotificationService, never()).sendToUser(any(), anyMap());
        verify(webSocketNotificationService, never()).hasActiveSession(userId);
        verify(deviceTokenRepository).findByUserId(userId);
    }

    @Test
    void shouldClearStalePresenceAndFallbackToFcmWithoutThrowing() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);

        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);
        when(webSocketNotificationService.hasActiveSession(userId)).thenReturn(false);
        when(deviceTokenRepository.findByUserId(userId)).thenReturn(List.of());

        assertThatCode(() -> notificationDeliveryService.deliver(notification))
                .doesNotThrowAnyException();

        verify(redisTemplate).delete("presence:" + userId);
        verify(webSocketNotificationService, never()).sendToUser(any(), anyMap());
        verify(deviceTokenRepository).findByUserId(userId);
    }

    @Test
    void shouldSendGroupedUpdateOverWsWhenUserOnline() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);
        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.TRUE);
        when(webSocketNotificationService.hasActiveSession(userId)).thenReturn(true);

        notificationDeliveryService.deliverGroupedUpdate(notification);

        verify(webSocketNotificationService).sendToUser(eq(userId), anyMap());
        verify(deviceTokenRepository, never()).findByUserId(userId);
    }

    @Test
    void shouldNotSendRepeatedFcmForGroupedUpdateWhenUserOffline() {
        UUID userId = UUID.randomUUID();
        Notification notification = notificationFor(userId);
        when(redisTemplate.hasKey("presence:" + userId)).thenReturn(Boolean.FALSE);

        notificationDeliveryService.deliverGroupedUpdate(notification);

        verify(webSocketNotificationService, never()).sendToUser(any(), anyMap());
        verify(deviceTokenRepository, never()).findByUserId(userId);
    }

    private Notification notificationFor(UUID userId) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUserId(userId);
        notification.setType(NotificationType.LIKE);
        notification.setPayload("{\"postId\":\"abc\"}");
        notification.setCreatedAt(Instant.now());
        return notification;
    }
}
