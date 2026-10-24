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

    public void evictAll() {
        summaryCache.clear();
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

    private record CachedUserSummary(UserSummary summary, Instant expiresAt) {}

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
