package com.example.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Thông tin người đối thoại trong hội thoại DIRECT. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerResponse {
    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private Boolean verified;
    private Instant verifiedUntil;
}
