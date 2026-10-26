package com.example.chat.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSessionRegistryTest {

    private final ChatSessionRegistry registry = new ChatSessionRegistry();

    private WebSocketSession openSession(String id) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    @DisplayName("Đăng ký nhiều tab của cùng user và gửi tới tất cả")
    void sendsToAllSessionsOfUser() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession tab1 = openSession("s1");
        WebSocketSession tab2 = openSession("s2");

        registry.register(userId, tab1);
        registry.register(userId, tab2);

        assertThat(registry.isOnline(userId)).isTrue();
        assertThat(registry.sendToUser(userId, "{\"type\":\"X\"}")).isTrue();

        verify(tab1).sendMessage(any(TextMessage.class));
        verify(tab2).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("Ngắt kết nối thì user offline")
    void unregisterMarksOffline() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = openSession("s1");

        registry.register(userId, session);
        registry.unregister(userId, session);

        assertThat(registry.isOnline(userId)).isFalse();
        assertThat(registry.sendToUser(userId, "x")).isFalse();
    }

    @Test
    @DisplayName("Session đã đóng thì bỏ qua, không ném lỗi")
    void skipsClosedSessions() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession closed = mock(WebSocketSession.class);
        when(closed.getId()).thenReturn("closed");
        when(closed.isOpen()).thenReturn(false);

        registry.register(userId, closed);

        assertThat(registry.sendToUser(userId, "x")).isFalse();
        verify(closed, never()).sendMessage(any());
    }

    @Test
    @DisplayName("Lỗi khi gửi không làm sập luồng")
    void sendFailureIsSwallowed() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession broken = openSession("broken");
        doThrow(new java.io.IOException("boom")).when(broken).sendMessage(any(TextMessage.class));

        registry.register(userId, broken);

        assertThat(registry.sendToUser(userId, "x")).isFalse();
    }

    @Test
    @DisplayName("Gửi cho nhiều user")
    void sendsToMultipleUsers() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        WebSocketSession sa = openSession("a");
        WebSocketSession sb = openSession("b");

        registry.register(a, sa);
        registry.register(b, sb);

        registry.sendToUsers(java.util.List.of(a, b), "{}");

        verify(sa).sendMessage(any(TextMessage.class));
        verify(sb).sendMessage(any(TextMessage.class));
        assertThat(registry.sessionCount()).isEqualTo(2);
    }
}
