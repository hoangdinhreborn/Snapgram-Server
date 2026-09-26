package com.example.media.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaUploadedEvent {

    private String mediaId;
    private String ownerId;
    private String mimeType;
    private String url;
    private String thumbnailUrl;
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    private Instant uploadedAt;
}
