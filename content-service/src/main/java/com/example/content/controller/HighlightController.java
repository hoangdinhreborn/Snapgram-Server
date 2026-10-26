package com.example.content.controller;

import com.example.content.dto.CreateHighlightRequest;
import com.example.content.dto.HighlightDetailResponse;
import com.example.content.dto.HighlightResponse;
import com.example.content.service.HighlightService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/highlights")
@RequiredArgsConstructor
@Tag(name = "Story Highlights API", description = "Quản lý tin nổi bật / story highlights trên trang cá nhân")
public class HighlightController {

    private final HighlightService highlightService;

    @Operation(summary = "Tạo tin nổi bật mới")
    @PostMapping
    public ResponseEntity<HighlightResponse> createHighlight(@Valid @RequestBody CreateHighlightRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(highlightService.createHighlight(userId, request));
    }

    @Operation(summary = "Lấy danh sách tin nổi bật của một người dùng")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<HighlightResponse>> getUserHighlights(@PathVariable UUID userId) {
        UUID viewerId = UserContext.getOptionalCurrentUserId();
        return ResponseEntity.ok(highlightService.getUserHighlights(userId, viewerId));
    }

    @Operation(summary = "Xem chi tiết tin nổi bật kèm danh sách story")
    @GetMapping("/{id}")
    public ResponseEntity<HighlightDetailResponse> getHighlightDetail(@PathVariable UUID id) {
        UUID viewerId = UserContext.getOptionalCurrentUserId();
        return ResponseEntity.ok(highlightService.getHighlightDetail(id, viewerId));
    }

    @Operation(summary = "Thêm story vào tin nổi bật")
    @PostMapping("/{id}/stories/{storyId}")
    public ResponseEntity<Void> addStoryToHighlight(
            @PathVariable UUID id,
            @PathVariable UUID storyId) {
        UUID userId = UserContext.getCurrentUserId();
        highlightService.addStoryToHighlight(id, storyId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xóa story khỏi tin nổi bật")
    @DeleteMapping("/{id}/stories/{storyId}")
    public ResponseEntity<Void> removeStoryFromHighlight(
            @PathVariable UUID id,
            @PathVariable UUID storyId) {
        UUID userId = UserContext.getCurrentUserId();
        highlightService.removeStoryFromHighlight(id, storyId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Xóa tin nổi bật")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHighlight(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        highlightService.deleteHighlight(id, userId);
        return ResponseEntity.noContent().build();
    }
}
