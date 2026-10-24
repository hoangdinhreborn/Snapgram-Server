package com.example.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
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
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = resolveUserId(session);
        sessionsByUser.computeIfAbsent(userId, key -> ConcurrentHashMap.newKeySet()).add(session);
        markOnline(userId);
        log.info("WebSocket connected for user {}", userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = resolveUserId(session);
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
        // Preferred: header injected by api-gateway after JWT verification
        String userId = session.getHandshakeHeaders().getFirst("X-User-Id");
        if (userId == null || userId.isBlank()) {
            // Fallback: browsers cannot set custom headers on WebSocket handshakes
            String query = session.getUri() != null ? session.getUri().getQuery() : null;
            if (query != null) {
                for (String part : query.split("&")) {
                    int eq = part.indexOf('=');
                    if (eq > 0 && "userId".equals(part.substring(0, eq))) {
                        userId = part.substring(eq + 1);
                        break;
                    }
                }
            }
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Missing X-User-Id header for websocket connection");
        }
        return UUID.fromString(userId);
    }
}
