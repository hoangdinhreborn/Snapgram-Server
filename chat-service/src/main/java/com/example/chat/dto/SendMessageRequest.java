package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    private String content;

    private String type; // TEXT, IMAGE, etc.
    private String mediaUrl;
}
