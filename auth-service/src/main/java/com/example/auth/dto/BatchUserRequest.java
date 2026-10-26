package com.example.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchUserRequest {

    @NotEmpty(message = "User IDs list must not be empty")
    @Size(max = 100, message = "Cannot request more than 100 users at once")
    private List<UUID> userIds;
}
