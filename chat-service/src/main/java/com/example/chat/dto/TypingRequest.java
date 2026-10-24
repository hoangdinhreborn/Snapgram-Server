package com.example.chat.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TypingRequest {
    private Boolean typing = Boolean.TRUE;
    /** Tên hiển thị để client hiển thị "X đang nhập...". */
    private String username;
    /** Chỉ dùng khi gọi qua WebSocket (REST lấy từ header). */
    private UUID conversationId;
}
