package com.example.content.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AuthServiceClient {

    private final RestClient restClient;
    private final Map<UUID, CachedUserSummary> summaryCache = new ConcurrentHashMap<>();
    private final Map<String, CachedBlockStatus> blockStatusCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedBlockedIds> blockedIdsCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 60; // 1 phút — hạ từ 300s để badge cập nhật nhanh
    /**
     * Xoá cache hồ sơ của một user — gọi khi nhận được event thay đổi hồ sơ
     * (vd: vừa mua Blue Tick) để lần đọc kế tiếp lấy dữ liệu mới ngay.
     */
    public void evict(UUID userId) {
        if (userId != null) {
            summaryCache.remove(userId);
        }
    }

    public void evictBlockCache(UUID blockerId, UUID blockedId) {
        if (blockerId != null) blockedIdsCache.remove(blockerId);
        if (blockedId != null) blockedIdsCache.remove(blockedId);
        if (blockerId != null && blockedId != null) {
            blockStatusCache.remove(blockPairKey(blockerId, blockedId));
        }
    }

    private String blockPairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public boolean isMutuallyBlocked(UUID userA, UUID userB) {
        if (userA == null || userB == null) return false;
        if (userA.equals(userB)) return false;

        String key = blockPairKey(userA, userB);
        Instant now = Instant.now();
        CachedBlockStatus cached = blockStatusCache.get(key);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.blocked;
        }

        try {
            Boolean blocked = restClient.get()
                    .uri("/api/users/internal/is-blocked?userA={a}&userB={b}", userA, userB)
                    .retrieve()
                    .body(Boolean.class);
            boolean isBlocked = Boolean.TRUE.equals(blocked);
            blockStatusCache.put(key, new CachedBlockStatus(isBlocked, now.plusSeconds(CACHE_TTL_SECONDS)));
            return isBlocked;
        } catch (Exception e) {
            log.warn("Lỗi kiểm tra block từ auth-service: {}", e.getMessage());
            return false;
        }
    }

    public List<UUID> getMutualBlockedUserIds(UUID userId) {
        if (userId == null) return Collections.emptyList();

        Instant now = Instant.now();
        CachedBlockedIds cached = blockedIdsCache.get(userId);
        if (cached != null && cached.expiresAt.isAfter(now)) {
            return cached.ids;
        }

        try {
            List<UUID> ids = restClient.get()
                    .uri("/api/users/internal/mutual-blocks/{userId}", userId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UUID>>() {});
            List<UUID> result = ids != null ? ids : Collections.emptyList();
            blockedIdsCache.put(userId, new CachedBlockedIds(result, now.plusSeconds(CACHE_TTL_SECONDS)));
            return result;
        } catch (Exception e) {
            log.warn("Lỗi lấy danh sách blocked ids từ auth-service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void evictAll() {
        summaryCache.clear();
        blockStatusCache.clear();
        blockedIdsCache.clear();
    }

    public AuthServiceClient(@Value("${app.auth.url:http://localhost:8081}") String authUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1200);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public Map<UUID, UserSummary> getUsersSummaryMap(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Instant now = Instant.now();
        Map<UUID, UserSummary> result = new HashMap<>();
        List<UUID> missingIds = new ArrayList<>();

        for (UUID id : userIds) {
            CachedUserSummary cached = summaryCache.get(id);
            if (cached != null && cached.expiresAt.isAfter(now)) {
                result.put(id, cached.summary);
            } else {
                missingIds.add(id);
            }
        }

        if (missingIds.isEmpty()) {
            return result;
        }

        try {
            BatchUserRequest request = new BatchUserRequest(missingIds);
            List<UserSummary> fetched = restClient.post()
                    .uri("/api/users/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<UserSummary>>() {});

            if (fetched != null) {
                Instant expiry = now.plusSeconds(CACHE_TTL_SECONDS);
                for (UserSummary s : fetched) {
                    if (s.getId() != null) {
                        try {
                            UUID uid = UUID.fromString(s.getId());
                            result.put(uid, s);
                            summaryCache.put(uid, new CachedUserSummary(s, expiry));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("AuthServiceClient get batch user profiles failed: {}", e.getMessage());
        }

        return result;
    }

    public UserSummary getUserSummary(UUID userId) {
        if (userId == null) return null;
        Map<UUID, UserSummary> map = getUsersSummaryMap(List.of(userId));
        return map.get(userId);
    }

    public void banUser(UUID userId, String reason) {
        if (userId == null) return;
        try {
            restClient.post()
                    .uri("/api/users/internal/{userId}/ban", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new InternalBanRequest(reason))
                    .retrieve()
                    .toBodilessEntity();
            log.info("AuthServiceClient banned user {} with reason: {}", userId, reason);
        } catch (Exception e) {
            log.warn("AuthServiceClient ban user {} failed: {}", userId, e.getMessage());
        }
    }

    public record InternalBanRequest(String reason) {}

    private record CachedUserSummary(UserSummary summary, Instant expiresAt) {}
    private record CachedBlockStatus(boolean blocked, Instant expiresAt) {}
    private record CachedBlockedIds(List<UUID> ids, Instant expiresAt) {}

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private String id;
        private String username;
        private String displayName;
        private String avatarUrl;
        private boolean privateAccount;
        /** Blue Tick còn hiệu lực (auth-service đã tính theo verified_until). */
        private boolean verified;
        private java.time.Instant verifiedUntil;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchUserRequest {
        private List<UUID> userIds;
    }
}
