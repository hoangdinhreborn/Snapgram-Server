package com.example.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class WebSocketNotificationService extends TextWebSocketHandler {

    private static final String PRESENCE_KEY_PREFIX = "presence:";
    private static final Duration PRESENCE_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final Map<UUID, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public WebSocketNotificationService(ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID userId = resolveUserId(session);
        if (userId == null) {
            log.warn("Closing WS notification connection due to missing/invalid X-User-Id: uri={}", session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unauthorized: Missing or invalid X-User-Id"));
            return;
        }
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        markOnline(userId);
        log.info("WebSocket connected for user {}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = resolveUserId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                sessionsByUser.remove(userId);
                markOffline(userId);
            }
        }
        log.info("WebSocket disconnected for user {}", userId);
    }

    /**
     * Heartbeat: a presence key must never outlive the WebSocket session that created it.
     * Refreshes the TTL of {@code presence:<userId>} for every user that still has at least
     * one open session, so long-lived connections are not treated as offline after 10 minutes.
     */
    @Scheduled(fixedDelayString = "${notification.presence.refresh-ms:120000}")
    public void refreshPresenceTtl() {
        int refreshed = 0;
        for (Map.Entry<UUID, Set<WebSocketSession>> entry : sessionsByUser.entrySet()) {
            if (hasOpenSession(entry.getValue())) {
                markOnline(entry.getKey());
                refreshed++;
            }
        }
        log.debug("Refreshed presence TTL for {} user(s) with open WebSocket sessions", refreshed);
    }

    /** True when the user still has at least one open WebSocket session in this instance. */
    public boolean hasActiveSession(UUID userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        return sessions != null && hasOpenSession(sessions);
    }

    private boolean hasOpenSession(Set<WebSocketSession> sessions) {
        return sessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    /** Publish presence so NotificationDeliveryService knows the user is reachable in real time. */
    private void markOnline(UUID userId) {
        try {
            redisTemplate.opsForValue().set(PRESENCE_KEY_PREFIX + userId, "1", PRESENCE_TTL);
        } catch (Exception e) {
            log.warn("Failed to mark presence online for user {}: {}", userId, e.getMessage());
        }
    }

    private void markOffline(UUID userId) {
        try {
            redisTemplate.delete(PRESENCE_KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to mark presence offline for user {}: {}", userId, e.getMessage());
        }
    }

    public void sendToUser(UUID userId, Object payload) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.error("Failed to send websocket notification to user {}", userId, e);
        }
    }

    private UUID resolveUserId(WebSocketSession session) {
        String userId = session.getHandshakeHeaders().getFirst("X-User-Id");
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userId.trim());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid X-User-Id header format for notification WS: {}", userId);
            return null;
        }
    }
}
