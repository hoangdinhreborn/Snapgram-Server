package com.example.content.controller;

import com.example.content.dto.CollectionResponse;
import com.example.content.dto.CreateCollectionRequest;
import com.example.content.dto.PostResponse;
import com.example.content.dto.SavePostRequest;
import com.example.content.service.SavedPostService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved")
@RequiredArgsConstructor
@Tag(name = "Saved Posts API", description = "Lưu bài đăng và bộ sưu tập")
public class SavedPostController {

    private final SavedPostService savedPostService;

    @Operation(summary = "Lưu bài đăng")
    @PostMapping("/{postId}")
    public ResponseEntity<Void> savePost(
            @PathVariable UUID postId,
            @RequestBody(required = false) SavePostRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        savedPostService.savePost(userId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Operation(summary = "Bỏ lưu bài đăng")
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> unsavePost(@PathVariable UUID postId) {
        UUID userId = UserContext.getCurrentUserId();
        savedPostService.unsavePost(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Danh sách bài đăng đã lưu")
    @GetMapping
    public ResponseEntity<Page<PostResponse>> getSavedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(savedPostService.getSavedPosts(userId, PageRequest.of(page, size)));
    }

    // ── Collections ──────────────────────────────────────────────────────────

    @Operation(summary = "Tạo bộ sưu tập")
    @PostMapping("/collections")
    public ResponseEntity<CollectionResponse> createCollection(@Valid @RequestBody CreateCollectionRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedPostService.createCollection(userId, request));
    }

    @Operation(summary = "Danh sách bộ sưu tập của tôi")
    @GetMapping("/collections")
    public ResponseEntity<List<CollectionResponse>> getMyCollections() {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(savedPostService.getMyCollections(userId));
    }

    @Operation(summary = "Xóa bộ sưu tập")
    @DeleteMapping("/collections/{collectionId}")
    public ResponseEntity<Void> deleteCollection(@PathVariable UUID collectionId) {
        UUID userId = UserContext.getCurrentUserId();
        savedPostService.deleteCollection(collectionId, userId);
        return ResponseEntity.noContent().build();
    }
}
