package com.example.chat.dto.message;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {

    /** Có thể rỗng khi gửi ảnh/GIF (chỉ cần mediaUrl). */
    @Size(max = 4000, message = "Tin nhắn tối đa 4000 ký tự")
    private String content;

    /** TEXT | IMAGE | GIF | STICKER */
    private String type;

    @Size(max = 1000)
    private String mediaUrl;
}
