package com.example.payment.util;

import com.example.payment.exception.AccessDeniedException;
import com.example.payment.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private UserContext() {
    }

    private static HttpServletRequest getRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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

    public static List<String> getUserRoles() {
        String rolesStr = getRequest().getHeader(HEADER_USER_ROLES);
        if (rolesStr == null || rolesStr.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesStr.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public static boolean isAdmin() {
        return getUserRoles().stream()
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .anyMatch(r -> r.equalsIgnoreCase("ADMIN"));
    }

    /** Chan endpoint admin: khong phai ADMIN -> 403. */
    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Access denied: ADMIN role required");
        }
    }
}
