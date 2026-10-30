package com.example.auth.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSummaryDto {
    private String id;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String role;
    private boolean privateAccount;
    /** Blue Tick còn hiệu lực — để content-service hiển thị huy hiệu cạnh tên tác giả. */
    private boolean verified;
    private Instant verifiedUntil;
}
