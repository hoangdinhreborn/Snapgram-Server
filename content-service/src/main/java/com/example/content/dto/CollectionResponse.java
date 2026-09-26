package com.example.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectionResponse {
    private String id;
    private String userId;
    private String name;
    private boolean isPrivate;
    private String coverPostId;
    private Instant createdAt;
}
