package com.example.recommender.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepExecutionDto {
    private String stepName;
    private String status;
    private String exitCode;
    private long readCount;
    private long writeCount;
    private long commitCount;
    private long rollbackCount;
    private long readSkipCount;
    private long processSkipCount;
    private long writeSkipCount;
    private long totalSkipCount;
}
