package com.example.content.event;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class EventIds {

    private EventIds() {
    }

    public static String stableFor(String topic, String... businessKeys) {
        return UUID.nameUUIDFromBytes((topic + ":" + String.join(":", businessKeys))
                .getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}