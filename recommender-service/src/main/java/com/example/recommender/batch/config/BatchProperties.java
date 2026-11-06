package com.example.recommender.batch.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "recommender.batch")
@Validated
@Getter
@Setter
public class BatchProperties {

    @Min(value = 1, message = "chunkSize must be at least 1")
    private int chunkSize = 20;

    @NotBlank(message = "trendingCron must not be blank")
    private String trendingCron = "0 0 */2 * * *";

    @NotBlank(message = "userRecCron must not be blank")
    private String userRecCron = "0 0 3 * * *";

    private boolean scheduledEnabled = false;

    @Min(value = 0, message = "skipLimit must be non-negative")
    private int skipLimit = 100;
}
