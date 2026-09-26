package com.example.content.controller;

import com.example.content.dto.CreateStoryRequest;
import com.example.content.dto.StoryResponse;
import com.example.content.dto.StoryViewerResponse;
import com.example.content.service.StoryService;
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
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Stories API", description = "Quản lý story")
public class StoryController {

    private final StoryService storyService;

    @Operation(summary = "Tạo story mới")
    @PostMapping
    public ResponseEntity<StoryResponse> createStory(@Valid @RequestBody CreateStoryRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(storyService.createStory(userId, request));
    }

    @Operation(summary = "Feed story")
    @GetMapping("/feed")
    public ResponseEntity<Page<StoryResponse>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(storyService.getStoryFeed(userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Chi tiết story")
    @GetMapping("/{id}")
    public ResponseEntity<StoryResponse> getStory(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(storyService.getStory(id, userId));
    }

    @Operation(summary = "Xóa story")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStory(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        storyService.deleteStory(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Đánh dấu đã xem story")
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> viewStory(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        storyService.viewStory(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Danh sách người xem (chỉ tác giả)")
    @GetMapping("/{id}/viewers")
    public ResponseEntity<Page<StoryViewerResponse>> getViewers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(storyService.getViewers(id, userId, PageRequest.of(page, size)));
    }
}
