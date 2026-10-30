package com.example.content.dto.ticket;

import com.example.content.entity.SupportTicketStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplySupportTicketRequest {

    @NotBlank(message = "Reply message must not be blank")
    private String reply;

    private SupportTicketStatus status;
}
