package com.example.chat.dto.message;

import lombok.Data;

import java.util.UUID;

@Data
public class MarkReadRequest {
    /** Tin nhắn cuối cùng người dùng đã thấy. Bỏ trống = tin mới nhất của hội thoại. */
    private UUID lastReadMessageId;
}
