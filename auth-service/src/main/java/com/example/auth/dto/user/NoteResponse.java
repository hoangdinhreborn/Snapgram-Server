package com.example.auth.dto.user;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class NoteResponse {
    private String id;
    private String content;
    private Instant createdAt;
    private Instant expiresAt;
}
