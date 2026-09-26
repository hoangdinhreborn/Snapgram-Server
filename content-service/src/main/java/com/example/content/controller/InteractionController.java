package com.example.content.controller;

import com.example.content.dto.InteractionRequest;
import com.example.content.service.InteractionService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/interact")
@RequiredArgsConstructor
@Tag(name = "Interactions API", description = "Tương tác với bài đăng (like, view, share, ...)")
public class InteractionController {

    private final InteractionService interactionService;

    @Operation(summary = "Ghi nhận tương tác (LIKE / VIEW / SHARE / SAVE / SKIP)")
    @PostMapping
    public ResponseEntity<Void> interact(
            @PathVariable UUID postId,
            @Valid @RequestBody InteractionRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        interactionService.interact(postId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bỏ like bài đăng")
    @DeleteMapping("/like")
    public ResponseEntity<Void> unlike(@PathVariable UUID postId) {
        UUID userId = UserContext.getCurrentUserId();
        interactionService.unlike(postId, userId);
        return ResponseEntity.noContent().build();
    }
}
