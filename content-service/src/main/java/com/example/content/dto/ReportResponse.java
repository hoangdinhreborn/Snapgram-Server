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

    // Target content preview (Admin inspection)
    private String targetContent;
    private java.util.List<String> targetMediaUrls;
    private String targetAuthorId;
    private String targetAuthorUsername;
    private String targetAuthorDisplayName;
    private String targetAuthorAvatarUrl;
    private String targetStatus;
    private Instant targetCreatedAt;
}
