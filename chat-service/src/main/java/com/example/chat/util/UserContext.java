package com.example.chat.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";

    private UserContext() {}

    public static UUID getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new RuntimeException("No active HTTP request context");
        }
        HttpServletRequest request = attributes.getRequest();
        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid X-User-Id format: must be a valid UUID");
            }
        }
        throw new RuntimeException("Missing authentication: X-User-Id header is required");
    }
}
