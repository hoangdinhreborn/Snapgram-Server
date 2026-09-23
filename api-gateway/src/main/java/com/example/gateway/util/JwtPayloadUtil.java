package com.example.gateway.util;

import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility để extract claims từ JWT payload mà KHÔNG verify signature.
 *
 * <p>Dùng sau khi auth-service đã confirm token hợp lệ (qua POST /api/auth/verify).
 * Gateway chỉ cần đọc claims để inject downstream headers:
 * X-User-Id, X-Username, X-User-Roles.
 *
 * <p>JWT format: base64url(header).base64url(payload).base64url(signature)
 */
@Component
public class JwtPayloadUtil {

    private static final int PAYLOAD_INDEX = 1;
    private static final int JWT_PARTS = 3;

    private final ObjectMapper objectMapper;

    public JwtPayloadUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Decode JWT payload thành Map claims.
     *
     * @param token JWT token string (với hoặc không có "Bearer " prefix)
     * @return Map chứa claims, hoặc empty map nếu decode thất bại
     */
    public Map<String, Object> extractPayload(String token) {
        try {
            // Strip "Bearer " prefix nếu có
            String raw = token.startsWith("Bearer ") ? token.substring(7) : token;

            String[] parts = raw.split("\\.");
            if (parts.length != JWT_PARTS) {
                return Collections.emptyMap();
            }

            // Dùng getUrlDecoder() vì JWT dùng Base64URL (không phải Base64 thông thường)
            byte[] decoded = Base64.getUrlDecoder().decode(parts[PAYLOAD_INDEX]);
            return objectMapper.readValue(decoded, new TypeReference<>() {});

        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Extract userId (JWT "sub" claim).
     */
    public String getUserId(Map<String, Object> payload) {
        Object sub = payload.get("sub");
        return sub != null ? sub.toString() : "";
    }

    /**
     * Extract username (JWT "username" claim).
     */
    public String getUsername(Map<String, Object> payload) {
        Object username = payload.get("username");
        return username != null ? username.toString() : "";
    }

    /**
     * Extract roles (JWT "roles" claim) thành comma-separated string.
     * Ví dụ: ["USER", "ADMIN"] → "USER,ADMIN"
     */
    @SuppressWarnings("unchecked")
    public String getRolesAsString(Map<String, Object> payload) {
        Object rolesObj = payload.get("roles");
        if (rolesObj instanceof List<?> roles) {
            return String.join(",", (List<String>) roles);
        }
        return "";
    }
}
