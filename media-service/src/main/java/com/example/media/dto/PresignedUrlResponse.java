package com.example.media.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PresignedUrlResponse {
    private String mediaId;
    private String uploadUrl;
    private String fileUrl;
    private Long expiresInSeconds;
}
