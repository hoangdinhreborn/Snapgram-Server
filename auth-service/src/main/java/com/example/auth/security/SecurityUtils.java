package com.example.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Utility to extract the authenticated user's UUID from SecurityContext or X-User-Id header.
 * Principal is set as UUID by JwtAuthenticationFilter.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        UUID id = getOptionalCurrentUserId();
        if (id == null) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return id;
    }

    public static UUID getOptionalCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID) {
            return (UUID) auth.getPrincipal();
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String headerVal = attrs.getRequest().getHeader("X-User-Id");
            if (headerVal != null && !headerVal.isBlank()) {
                try {
                    return UUID.fromString(headerVal.trim());
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return null;
    }
}
