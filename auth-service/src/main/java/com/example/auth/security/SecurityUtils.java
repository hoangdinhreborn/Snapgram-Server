package com.example.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility to extract the authenticated user's UUID from SecurityContext.
 * Principal is set as UUID by JwtAuthenticationFilter.
 */
public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID)) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return (UUID) auth.getPrincipal();
    }
}
