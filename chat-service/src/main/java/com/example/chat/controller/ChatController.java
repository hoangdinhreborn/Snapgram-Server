package com.example.chat.controller;

import com.example.chat.dto.ConversationResponse;
import com.example.chat.dto.CreateDirectConversationRequest;
import com.example.chat.dto.MarkReadRequest;
import com.example.chat.dto.MessagePageResponse;
import com.example.chat.dto.MessageResponse;
import com.example.chat.dto.SendMessageRequest;
import com.example.chat.dto.TypingRequest;
import com.example.chat.dto.UnreadCountResponse;
import com.example.chat.service.ChatService;
import com.example.chat.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conv")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations() {
        return ResponseEntity.ok(chatService.getMyConversations(UserContext.getCurrentUserId()));
    }

    /** Tổng số tin chưa đọc — dùng cho badge ở sidebar/navbot. */
    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(new UnreadCountResponse(chatService.getTotalUnread(userId)));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> getOrCreateDirectConversation(
            @Valid @RequestBody CreateDirectConversationRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.getOrCreateDirectConversation(userId, request.getTargetUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(@PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getConversation(id, UserContext.getCurrentUserId()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<MessagePageResponse> getConversationMessages(
            @PathVariable UUID id,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(chatService.getConversationMessages(id, userId, before, page, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(id, userId, request));
    }

    @DeleteMapping("/{id}/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID id, @PathVariable UUID messageId) {
        chatService.deleteMessage(id, messageId, UserContext.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<ConversationResponse> markRead(
            @PathVariable UUID id,
            @RequestBody(required = false) MarkReadRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        UUID lastReadMessageId = request != null ? request.getLastReadMessageId() : null;
        return ResponseEntity.ok(chatService.markRead(id, userId, lastReadMessageId));
    }

    @PostMapping("/{id}/typing")
    public ResponseEntity<Void> typing(
            @PathVariable UUID id,
            @RequestBody(required = false) TypingRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        boolean typing = request == null || request.getTyping() == null || request.getTyping();
        String username = request != null ? request.getUsername() : null;
        chatService.sendTyping(id, userId, username, typing);
        return ResponseEntity.noContent().build();
    }
}
