package com.example.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentGrantPropertiesTest {

    /** Dam bao "30d" (dinh dang env/yml mac dinh) bind duoc thanh Duration. */
    @Test
    void bindsSimpleDayDuration() {
        MapConfigurationPropertySource source =
                new MapConfigurationPropertySource(Map.of("payment.grants.BLUE_TICK", "30d"));

        PaymentGrantProperties properties = new Binder(source)
                .bind("payment", PaymentGrantProperties.class)
                .orElseThrow(IllegalStateException::new);

        assertEquals(Duration.ofDays(30), properties.getGrants().get("BLUE_TICK"));
    }

    @Test
    void defaultGrantsMap_isEmptyNotNull() {
        assertTrue(new PaymentGrantProperties().getGrants().isEmpty());
    }
}
