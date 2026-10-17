package com.example.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_event_log")
@Getter
@Setter
public class PaymentEventLog {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 32)
    private String provider = "VNPAY";

    @Column(name = "response_code", length = 8)
    private String responseCode;

    @Column(name = "transaction_no", length = 64)
    private String transactionNo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }
}
