package com.example.content.controller;

import com.example.content.dto.FollowResponse;
import com.example.content.service.FollowService;
import com.example.content.util.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/follows")
@RequiredArgsConstructor
@Tag(name = "Follows API", description = "Quản lý follow")
public class FollowController {

    private final FollowService followService;

    @Operation(summary = "Follow user")
    @PostMapping("/{userId}")
    public ResponseEntity<FollowResponse> follow(@PathVariable UUID userId) {
        UUID currentUserId = UserContext.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(followService.follow(currentUserId, userId));
    }

    @Operation(summary = "Unfollow user")
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unfollow(@PathVariable UUID userId) {
        UUID currentUserId = UserContext.getCurrentUserId();
        followService.unfollow(currentUserId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Chấp nhận follow request")
    @PostMapping("/requests/{followId}/accept")
    public ResponseEntity<FollowResponse> acceptRequest(@PathVariable UUID followId) {
        UUID currentUserId = UserContext.getCurrentUserId();
        return ResponseEntity.ok(followService.acceptRequest(followId, currentUserId));
    }

    @Operation(summary = "Từ chối follow request")
    @PostMapping("/requests/{followId}/reject")
    public ResponseEntity<Void> rejectRequest(@PathVariable UUID followId) {
        UUID currentUserId = UserContext.getCurrentUserId();
        followService.rejectRequest(followId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Danh sách followers")
    @GetMapping("/followers")
    public ResponseEntity<Page<FollowResponse>> getFollowers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(followService.getFollowers(userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Danh sách following")
    @GetMapping("/following")
    public ResponseEntity<Page<FollowResponse>> getFollowing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(followService.getFollowing(userId, PageRequest.of(page, size)));
    }

    @Operation(summary = "Danh sách follow requests đang chờ")
    @GetMapping("/requests")
    public ResponseEntity<Page<FollowResponse>> getPendingRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        size = Math.min(size, 50);
        return ResponseEntity.ok(followService.getPendingRequests(userId, PageRequest.of(page, size)));
    }
}
