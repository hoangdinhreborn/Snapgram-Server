package com.example.notification.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallEvent {
    private String callerId;
    private String calleeId;
    private String callId;
    private Boolean missed;
    private Instant createdAt;
}