package com.example.content.service;

import com.example.content.entity.UserPrivacyView;
import com.example.content.repository.UserPrivacyViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Listens to user.events from auth-service to sync privacy settings.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserEventConsumer {

    private final UserPrivacyViewRepository userPrivacyViewRepository;
    private final FollowService followService;
    private final com.example.content.client.AuthServiceClient authServiceClient;
    /** Có thể không tồn tại (module chưa bật caching) — nên dùng ObjectProvider. */
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider;

    /** Xoá cache "posts" nếu caching đang bật — vì PostResponse cache kèm authorVerified. */
    private void evictPostCache() {
        try {
            var cacheManager = cacheManagerProvider.getIfAvailable();
            if (cacheManager == null) return;
            var cache = cacheManager.getCache("posts");
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("Không xoá được cache posts: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "user.events", groupId = "content-service")
    @Transactional
    public void handleUserEvent(Map<String, Object> payload) {
        try {
            String eventType = (String) payload.get("eventType");
            if (eventType == null) return;

            switch (eventType) {
                case "PRIVACY_CHANGED" -> {
                    String userIdStr = (String) payload.get("userId");
                    Object isPrivateObj = payload.get("isPrivate");

                    if (userIdStr == null || isPrivateObj == null) return;

                    UUID userId = UUID.fromString(userIdStr);
                    boolean isPrivate = Boolean.TRUE.equals(isPrivateObj);

                    UserPrivacyView view = userPrivacyViewRepository.findById(userId)
                            .orElseGet(() -> {
                                UserPrivacyView v = new UserPrivacyView();
                                v.setUserId(userId);
                                return v;
                            });
                    view.setPrivate(isPrivate);
                    userPrivacyViewRepository.save(view);

                    log.info("Updated privacy for user {}: isPrivate={}", userId, isPrivate);
                }
                case "USER_BLOCKED" -> {
                    String blockerIdStr = (String) payload.get("blockerId");
                    String blockedIdStr = (String) payload.get("blockedId");

                    if (blockerIdStr != null && blockedIdStr != null) {
                        UUID blockerId = UUID.fromString(blockerIdStr);
                        UUID blockedId = UUID.fromString(blockedIdStr);
                        followService.removeMutualFollow(blockerId, blockedId);
                        authServiceClient.evictBlockCache(blockerId, blockedId);
                        evictPostCache();
                        log.info("Removed mutual follow and evicted block cache between {} and {} on USER_BLOCKED", blockerId, blockedId);
                    }
                }
                case "USER_UNBLOCKED" -> {
                    String blockerIdStr = (String) payload.get("blockerId");
                    String blockedIdStr = (String) payload.get("blockedId");

                    if (blockerIdStr != null && blockedIdStr != null) {
                        UUID blockerId = UUID.fromString(blockerIdStr);
                        UUID blockedId = UUID.fromString(blockedIdStr);
                        authServiceClient.evictBlockCache(blockerId, blockedId);
                        evictPostCache();
                        log.info("Evicted block cache between {} and {} on USER_UNBLOCKED", blockerId, blockedId);
                    }
                }
                case "BLUE_TICK_CHANGED" -> {
                    String userIdStr = (String) payload.get("userId");
                    if (userIdStr != null) {
                        UUID userId = UUID.fromString(userIdStr);
                        // 1) Xoá cache hồ sơ user để lần đọc kế tiếp lấy verified mới
                        authServiceClient.evict(userId);
                        // 2) Xoá cache bài viết vì PostResponse có chứa authorVerified
                        evictPostCache();
                        log.info("Evicted user summary + post cache for {} on BLUE_TICK_CHANGED", userId);
                    }
                }
                default -> log.debug("Ignored user event type in content-service: {}", eventType);
            }
        } catch (Exception e) {
            log.warn("Failed to process user.events payload: {}", e.getMessage());
        }
    }
}
