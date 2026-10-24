package com.example.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WebSocketNotificationServiceTest {

    private static final Duration PRESENCE_TTL = Duration.ofMinutes(10);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private WebSocketNotificationService webSocketNotificationService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        webSocketNotificationService = new WebSocketNotificationService(new ObjectMapper(), redisTemplate);
    }

    @Test
    void shouldReportActiveSessionForOpenWebSocket() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = sessionFor(userId, true);

        webSocketNotificationService.afterConnectionEstablished(session);

        assertThat(webSocketNotificationService.hasActiveSession(userId)).isTrue();
        verify(valueOperations).set("presence:" + userId, "1", PRESENCE_TTL);
    }

    @Test
    void shouldNotReportActiveSessionWhenSessionIsClosed() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = sessionFor(userId, true);

        webSocketNotificationService.afterConnectionEstablished(session);
        when(session.isOpen()).thenReturn(false);

        assertThat(webSocketNotificationService.hasActiveSession(userId)).isFalse();
    }

    @Test
    void shouldNotReportActiveSessionForUnknownUser() {
        assertThat(webSocketNotificationService.hasActiveSession(UUID.randomUUID())).isFalse();
    }

    @Test
    void shouldRefreshPresenceTtlOnlyForUsersWithOpenSessions() {
        UUID connectedUser = UUID.randomUUID();
        UUID disconnectedUser = UUID.randomUUID();
        WebSocketSession openSession = sessionFor(connectedUser, true);
        WebSocketSession closedSession = sessionFor(disconnectedUser, false);

        webSocketNotificationService.afterConnectionEstablished(openSession);
        webSocketNotificationService.afterConnectionEstablished(closedSession);
        reset(valueOperations);
        webSocketNotificationService.refreshPresenceTtl();

        verify(valueOperations).set("presence:" + connectedUser, "1", PRESENCE_TTL);
        verify(valueOperations, never()).set(eq("presence:" + disconnectedUser), anyString(), any(Duration.class));
    }

    private WebSocketSession sessionFor(UUID userId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", userId.toString());
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.isOpen()).thenReturn(open);
        return session;
    }
}
