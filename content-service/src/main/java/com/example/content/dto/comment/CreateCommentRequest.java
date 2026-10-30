package com.example.content.dto.comment;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCommentRequest {
    /** Có thể rỗng nếu bình luận chỉ gửi media (ảnh/GIF/sticker). */
    @Size(max = 2000, message = "Comment max 2000 characters")
    private String content;

    private UUID parentCommentId;

    /** URL ảnh/GIF đã upload, hoặc emoji nếu là sticker. */
    @Size(max = 1000)
    private String mediaUrl;

    /** IMAGE | GIF | STICKER */
    @Size(max = 20)
    private String mediaType;
}
