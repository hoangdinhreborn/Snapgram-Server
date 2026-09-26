package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportResponse {
    private String id;
    private String reporterId;
    private String targetType;
    private String targetId;
    private String reason;
    private String description;
    private String status;
    private Instant createdAt;
}
