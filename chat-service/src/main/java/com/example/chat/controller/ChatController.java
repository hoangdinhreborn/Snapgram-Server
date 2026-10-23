package com.example.chat.controller;

import com.example.chat.dto.ConversationResponse;
import com.example.chat.dto.CreateDirectConversationRequest;
import com.example.chat.dto.MessageResponse;
import com.example.chat.dto.SendMessageRequest;
import com.example.chat.service.ChatService;
import com.example.chat.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conv")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations() {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(chatService.getMyConversations(userId));
    }

    @PostMapping
    public ResponseEntity<ConversationResponse> getOrCreateDirectConversation(
            @Valid @RequestBody CreateDirectConversationRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.getOrCreateDirectConversation(userId, request.getTargetUserId()));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getConversationMessages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(chatService.getConversationMessages(id, userId, page, size));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.sendMessage(id, userId, request));
    }
}
