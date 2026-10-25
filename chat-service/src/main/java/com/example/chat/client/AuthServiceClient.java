package com.example.chat.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lấy thông tin công khai của user từ auth-service (/api/users/batch)
 * để hiển thị đúng username/avatar của người đối thoại.
 */
@Component
@Slf4j
public class AuthServiceClient {

    private final RestClient restClient;
    private final Map<String, CachedBlockStatus> blockStatusCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedBlockedIds> blockedIdsCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 60;

    public AuthServiceClient(@Value("${app.auth.url:http://localhost:8081}") String authUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .build();
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
            log.warn("Lỗi kiểm tra block từ auth-service trong chat-service: {}", e.getMessage());
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

    /** Trả về map userId -> summary; lỗi mạng thì trả map rỗng để chat vẫn hoạt động. */
    public Map<UUID, UserSummary> getUsersSummaryMap(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            BatchUserRequest request = new BatchUserRequest(List.copyOf(userIds));
            List<UserSummary> fetched = restClient.post()
                    .uri("/api/users/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<UserSummary>>() {});

            Map<UUID, UserSummary> result = new HashMap<>();
            if (fetched != null) {
                for (UserSummary summary : fetched) {
                    if (summary == null || summary.getId() == null) continue;
                    try {
                        result.put(UUID.fromString(summary.getId()), summary);
                    } catch (IllegalArgumentException ignored) {
                        // id không phải UUID -> bỏ qua
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Không lấy được user summary từ auth-service: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public UserSummary getUserSummary(UUID userId) {
        if (userId == null) return null;
        return getUsersSummaryMap(List.of(userId)).get(userId);
    }

    private record CachedBlockStatus(boolean blocked, Instant expiresAt) {}
    private record CachedBlockedIds(List<UUID> ids, Instant expiresAt) {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BatchUserRequest {
        private List<UUID> userIds;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserSummary {
        private String id;
        private String username;
        private String displayName;
        private String avatarUrl;
        private boolean privateAccount;
        private boolean verified;
        private Instant verifiedUntil;
    }
}
