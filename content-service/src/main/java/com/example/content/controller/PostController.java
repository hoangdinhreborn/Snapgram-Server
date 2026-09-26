package com.example.content.controller;

import com.example.content.dto.*;
import com.example.content.service.PostService;
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
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts API", description = "Quản lý bài đăng")
public class PostController {

    private final PostService postService;

    @Operation(summary = "Tạo bài đăng mới")
    @PostMapping
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody CreatePostRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(userId, request));
    }

    @Operation(summary = "Lấy chi tiết bài đăng")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPost(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(postService.getPost(id, userId));
    }

    @Operation(summary = "Sửa bài đăng")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePostRequest request) {
        UUID userId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(postService.updatePost(id, userId, request));
    }

    @Operation(summary = "Xóa bài đăng")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        postService.deletePost(id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Bài đăng của tôi")
    @GetMapping("/me")
    public ResponseEntity<Page<PostResponse>> getMyPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(postService.getMyPosts(userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Bài đăng của user khác")
    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<PostResponse>> getUserPosts(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID requesterId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(postService.getUserPosts(userId, requesterId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Feed cá nhân")
    @GetMapping("/feed")
    public ResponseEntity<Page<PostResponse>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(postService.getFeed(userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Khám phá")
    @GetMapping("/explore")
    public ResponseEntity<Page<PostResponse>> explore(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        return ResponseEntity.ok(postService.getExplorePosts(PageRequest.of(page, size)));
    }
}
