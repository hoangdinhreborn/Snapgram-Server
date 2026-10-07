package com.example.recommender.util;

import com.example.recommender.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USERNAME = "X-Username";

    private UserContext() {}

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("No active HTTP request context");
        }
        return attributes.getRequest();
    }

    public static UUID getCurrentUserId() {
        String userIdStr = getRequest().getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid X-User-Id format: must be a valid UUID");
            }
        }
        throw new UnauthorizedException("Missing authentication: X-User-Id header is required");
    }

    public static UUID getCurrentUserIdOrNull() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return null;
        String userIdStr = attributes.getRequest().getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isBlank()) {
            try {
                return UUID.fromString(userIdStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    public static String getUsername() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) return "";
        String username = attributes.getRequest().getHeader(HEADER_USERNAME);
        return username != null ? username.trim() : "";
    }
}
