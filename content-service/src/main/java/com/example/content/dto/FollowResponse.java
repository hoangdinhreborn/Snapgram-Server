package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FollowResponse {
    private String id;
    private String followerId;
    private String followingId;
    private String status;
    private Instant createdAt;
}
