package com.example.notification.controller;

import com.example.notification.dto.DeviceTokenRequest;
import com.example.notification.entity.DeviceToken;
import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import com.example.notification.service.DeviceTokenService;
import com.example.notification.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenService deviceTokenService;

    @GetMapping
    public ResponseEntity<Page<Notification>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UserContext.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        Page<Notification> notifications;

        if (unreadOnly) {
            notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return ResponseEntity.ok(notifications);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        UUID userId = UserContext.getCurrentUserId();
        notificationRepository.findById(id).ifPresent(notification -> {
            if (notification.getUserId().equals(userId)) {
                notification.setIsRead(true);
                notificationRepository.save(notification);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        UUID userId = UserContext.getCurrentUserId();
        Page<Notification> notifications = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, false, PageRequest.of(0, 1000));
        for (Notification notification : notifications.getContent()) {
            notification.setIsRead(true);
        }
        notificationRepository.saveAll(notifications.getContent());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices")
    public ResponseEntity<Map<String, String>> registerDevice(@RequestBody DeviceTokenRequest request) {
        UUID userId = UserContext.getCurrentUserId();

        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "token is required"));
        }

        DeviceToken deviceToken = deviceTokenService.saveToken(userId, request);
        return ResponseEntity.ok(Map.of(
                "message", "device token registered",
                "userId", userId.toString(),
                "deviceId", deviceToken.getId().toString()
        ));
    }
}
