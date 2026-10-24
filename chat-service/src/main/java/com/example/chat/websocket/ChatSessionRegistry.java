package com.example.chat.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giữ các WebSocket session đang mở, nhóm theo userId.
 *
 * Lưu ý: registry nằm trong bộ nhớ nên chỉ chính xác khi chat-service chạy 1 instance.
 * Khi scale ngang cần thay bằng Redis pub/sub — xem TODO ở ChatRealtimeService.
 */
@Component
@Slf4j
public class ChatSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.debug("Chat WS connected userId={} total={}", userId, sessionCount());
    }

    public void unregister(UUID userId, WebSocketSession session) {
        if (userId == null || session == null) return;
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(session);
        if (userSessions.isEmpty()) {
            sessions.remove(userId);
        }
        log.debug("Chat WS disconnected userId={} total={}", userId, sessionCount());
    }

    public boolean isOnline(UUID userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }

    public int sessionCount() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    /** Gửi text cho tất cả session của 1 user. Trả về true nếu đã gửi được ít nhất 1 session. */
    public boolean sendToUser(UUID userId, String payload) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) return false;

        boolean delivered = false;
        for (WebSocketSession session : userSessions) {
            if (sendRaw(session, payload)) {
                delivered = true;
            }
        }
        return delivered;
    }

    /** Gửi cho nhiều user cùng lúc. */
    public void sendToUsers(Collection<UUID> userIds, String payload) {
        if (userIds == null) return;
        for (UUID userId : userIds) {
            sendToUser(userId, payload);
        }
    }

    private boolean sendRaw(WebSocketSession session, String payload) {
        if (session == null || !session.isOpen()) return false;
        try {
            // WebSocketSession không thread-safe: đồng bộ theo session khi gửi song song
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.debug("Gửi WS thất bại, bỏ session {}: {}", session.getId(), e.getMessage());
            return false;
        }
    }
}
