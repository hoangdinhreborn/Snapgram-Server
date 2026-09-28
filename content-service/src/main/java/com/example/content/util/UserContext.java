package com.example.content.util;

import com.example.content.exception.AccessDeniedException;
import com.example.content.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID    = "X-User-Id";
    public static final String HEADER_USERNAME   = "X-Username";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private UserContext() {}

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new UnauthorizedException("No active HTTP request context");
        }
        return attributes.getRequest();
    }

    /**
     * Lấy UUID của người dùng hiện tại từ header X-User-Id.
     * Ném UnauthorizedException (401) nếu thiếu hoặc sai format.
     */
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

    /**
     * Lấy Username từ header X-Username.
     */
    public static String getUsername() {
        String username = getRequest().getHeader(HEADER_USERNAME);
        return username != null ? username.trim() : "";
    }

    /**
     * Lấy danh sách Roles từ header X-User-Roles (ví dụ: ["USER", "ADMIN"]).
     */
    public static List<String> getUserRoles() {
        String rolesStr = getRequest().getHeader(HEADER_USER_ROLES);
        if (rolesStr == null || rolesStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Kiểm tra user hiện tại có sở hữu role cụ thể không.
     */
    public static boolean hasRole(String role) {
        if (role == null || role.isBlank()) return false;
        String normalized = role.startsWith("ROLE_") ? role.substring(5) : role;
        return getUserRoles().stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .anyMatch(r -> r.equalsIgnoreCase(normalized));
    }

    /**
     * Kiểm tra nhanh user hiện tại có role ADMIN hay không.
     */
    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Bảo vệ endpoint Admin: nếu không phải ADMIN, ném AccessDeniedException (HTTP 403 Forbidden).
     */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied: ADMIN role required");
        }
    }
}
