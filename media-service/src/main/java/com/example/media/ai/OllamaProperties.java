package com.example.media.ai;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.ai.ollama")
@Getter
@Setter
public class OllamaProperties {
    private String baseUrl;
    private String model;
    private String apiKey;
    private int timeoutSeconds = 30;
    private double temperature = 0.7;
}
