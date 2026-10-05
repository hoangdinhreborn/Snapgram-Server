package com.example.content.entity;

public enum Visibility {
    PUBLIC,
    FOLLOWERS,
    FOLLOWERS_ONLY,
    CLOSE_FRIENDS,
    PRIVATE;

    public static Visibility parse(String val) {
        if (val == null || val.isBlank()) {
            return PUBLIC;
        }
        String normalized = val.trim().toUpperCase();
        if ("FOLLOWERS_ONLY".equals(normalized) || "FOLLOWERS".equals(normalized)) {
            return FOLLOWERS;
        }
        try {
            return Visibility.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return PUBLIC;
        }
    }
}

