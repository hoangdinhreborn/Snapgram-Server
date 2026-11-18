package com.example.chat.websocket;

import com.example.chat.service.ChatRealtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ChatWebSocketHandlerTest {

    private ChatSessionRegistry sessionRegistry;
    private ChatRealtimeService realtimeService;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(ChatSessionRegistry.class);
        realtimeService = mock(ChatRealtimeService.class);
        handler = new ChatWebSocketHandler(sessionRegistry, realtimeService);
    }

    @Test
    @DisplayName("Kết nối thành công khi có X-User-Id và X-Username hợp lệ")
    void connectionEstablished_WithValidHeaders_RegistersSession() throws Exception {
        UUID userId = UUID.randomUUID();
        String username = "alice";

        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-1");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", userId.toString());
        headers.add("X-Username", username);
        when(session.getHandshakeHeaders()).thenReturn(headers);

        Map<String, Object> attributes = new HashMap<>();
        when(session.getAttributes()).thenReturn(attributes);

        handler.afterConnectionEstablished(session);

        verify(sessionRegistry).register(userId, session);
        verify(session, never()).close(any());
        assertThat(attributes.get("chat.userId")).isEqualTo(userId);
        assertThat(attributes.get("chat.username")).isEqualTo(username);
    }

    @Test
    @DisplayName("Đóng kết nối với POLICY_VIOLATION khi thiếu header X-User-Id")
    void connectionEstablished_MissingUserId_ClosesWithPolicyViolation() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-2");
        when(session.getHandshakeHeaders()).thenReturn(new HttpHeaders());

        handler.afterConnectionEstablished(session);

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        verify(sessionRegistry, never()).register(any(), any());
    }

    @Test
    @DisplayName("Đóng kết nối với POLICY_VIOLATION khi header X-User-Id không phải UUID hợp lệ")
    void connectionEstablished_InvalidUserId_ClosesWithPolicyViolation() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-3");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "malicious-user-id");
        when(session.getHandshakeHeaders()).thenReturn(headers);

        handler.afterConnectionEstablished(session);

        verify(session).close(argThat(status -> status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
        verify(sessionRegistry, never()).register(any(), any());
    }

    @Test
    @DisplayName("Sau khi ngắt kết nối thì unregister khỏi sessionRegistry")
    void connectionClosed_UnregistersSession() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("sess-4");
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", userId.toString());
        when(session.getHandshakeHeaders()).thenReturn(headers);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(sessionRegistry).unregister(userId, session);
    }
}
