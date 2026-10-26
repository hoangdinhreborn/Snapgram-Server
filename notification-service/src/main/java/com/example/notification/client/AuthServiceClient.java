package com.example.notification.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class AuthServiceClient {

    private final RestClient restClient;
    private final Map<String, CachedBlockStatus> blockStatusCache = new ConcurrentHashMap<>();
    private final Map<UUID, CachedBlockedIds> blockedIdsCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_SECONDS = 60;

    public AuthServiceClient(@Value("${app.auth.url:http://localhost:8081}") String authUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1200);
        requestFactory.setReadTimeout(2000);

        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .requestFactory(requestFactory)
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

    private record CachedBlockStatus(boolean blocked, Instant expiresAt) {}
    private record CachedBlockedIds(List<UUID> ids, Instant expiresAt) {}
}
