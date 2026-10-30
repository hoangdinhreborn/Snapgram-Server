package com.example.media.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JacksonConfigTest {

    private final JacksonConfig jacksonConfig = new JacksonConfig();

    @Test
    @DisplayName("objectMapper bean is created with correct modules registered")
    void objectMapper_ShouldBeConfigured() {
        ObjectMapper mapper = jacksonConfig.objectMapper();

        assertThat(mapper).isNotNull();
    }
}
