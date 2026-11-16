package com.example.recommender.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobHistoryResponse {
    private int totalExecutions;
    private List<JobExecutionResponse> executions;
}
