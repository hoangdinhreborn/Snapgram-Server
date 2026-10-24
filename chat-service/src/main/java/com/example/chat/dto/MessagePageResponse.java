package com.example.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Trang tin nhắn theo thứ tự thời gian tăng dần (cũ → mới) cho UI chat. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessagePageResponse {
    private List<MessageResponse> content;
    private boolean hasMore;
    /** id tin cũ nhất trong trang — dùng làm `before` cho lần tải tiếp theo. */
    private String nextCursor;
    private long totalElements;
}
