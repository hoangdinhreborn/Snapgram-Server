package com.example.media.util;

import com.example.media.exception.UnauthorizedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";

    private UserContext() {}

    public static UUID getCurrentUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String userIdStr = attributes.getRequest().getHeader(HEADER_USER_ID);
            if (userIdStr != null && !userIdStr.isBlank()) {
                try {
                    return UUID.fromString(userIdStr.trim());
                } catch (IllegalArgumentException e) {
                    throw new UnauthorizedException("Invalid X-User-Id format: must be a valid UUID");
                }
            }
        }
        throw new UnauthorizedException("Missing authentication: X-User-Id header is required");
    }
}
