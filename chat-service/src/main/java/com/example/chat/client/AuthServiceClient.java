package com.example.chat.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

/**
 * Lấy thông tin công khai của user từ auth-service (/api/users/batch)
 * để hiển thị đúng username/avatar của người đối thoại.
 */
@Component
@Slf4j
public class AuthServiceClient {

    private final RestClient restClient;

    public AuthServiceClient(@Value("${app.auth.url:http://localhost:8081}") String authUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(authUrl)
                .build();
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
