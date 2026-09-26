package com.example.content.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePostRequest {
    private String contentType; // IMAGE, VIDEO, CAROUSEL, TEXT (auto-detected if null)

    @Size(max = 2200, message = "Caption max 2200 characters")
    private String caption;

    // Single media fallback (backward-compatible)
    private UUID mediaId;
    private String mediaUrl;

    // Multiple media items (for CAROUSEL / albums)
    private List<PostMediaRequest> mediaList;

    private String tags;
    private String visibility = "PUBLIC"; // PUBLIC, FOLLOWERS, CLOSE_FRIENDS, PRIVATE
    private List<String> hashtags;
    private List<UUID> mentionedUserIds;
}
