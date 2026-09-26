package com.example.content.controller;

import com.example.content.dto.CommentResponse;
import com.example.content.dto.CreateCommentRequest;
import com.example.content.service.CommentService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments API", description = "Quản lý bình luận")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Tạo bình luận / reply")
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createComment(postId, userId, request));
    }

    @Operation(summary = "Lấy danh sách bình luận gốc")
    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getRootComments(
            @PathVariable UUID postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(commentService.getRootComments(postId, userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Lấy replies của bình luận")
    @GetMapping("/{commentId}/replies")
    public ResponseEntity<Page<CommentResponse>> getReplies(
            @PathVariable UUID postId,
            @PathVariable UUID commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(commentService.getReplies(postId, commentId, userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Xóa bình luận")
    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable UUID postId,
            @PathVariable UUID commentId) {
        UUID userId = UserContext.getCurrentUserId();
        commentService.deleteComment(postId, commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Like bình luận")
    @PostMapping("/{commentId}/like")
    public ResponseEntity<Void> likeComment(@PathVariable UUID postId, @PathVariable UUID commentId) {
        UUID userId = UserContext.getCurrentUserId();
        commentService.likeComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unlike bình luận")
    @DeleteMapping("/{commentId}/like")
    public ResponseEntity<Void> unlikeComment(@PathVariable UUID postId, @PathVariable UUID commentId) {
        UUID userId = UserContext.getCurrentUserId();
        commentService.unlikeComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }
}
