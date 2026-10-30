package com.example.content.dto.follow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Trạng thái follow của người xem hiện tại với một user khác. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FollowStatusResponse {
    /** Đã có quan hệ follow (kể cả đang PENDING) hay chưa. */
    private boolean following;
    /** ACCEPTED | PENDING | null khi chưa follow. */
    private String status;
}
