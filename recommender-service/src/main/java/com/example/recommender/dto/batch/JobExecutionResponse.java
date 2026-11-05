package com.example.recommender.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionResponse {
    private Long jobExecutionId;
    private String jobName;
    private String status;
    private String exitCode;
    private String exitDescription;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMs;
    private List<StepExecutionDto> steps;

    public static Long calculateDuration(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return Duration.between(start, end).toMillis();
        }
        return null;
    }
}
