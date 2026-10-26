package com.example.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
public class PaymentOrder {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @Column(nullable = false, length = 32)
    private String provider = "VNPAY";

    @Column(name = "product_code", nullable = false, length = 64)
    private String productCode;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 8)
    private String currency = "VND";

    @Column(name = "order_info", length = 255)
    private String orderInfo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Ma tra ve cua provider (VNPay vnp_ResponseCode / MoMo resultCode). */
    @Column(name = "provider_response_code", length = 8)
    private String providerResponseCode;

    /** Kenh thanh toan: VNPay = bankCode (NCB...), MoMo = payType (web/qr...). */
    @Column(name = "provider_channel", length = 32)
    private String providerChannel;

    @Column(name = "provider_pay_date")
    private Instant providerPayDate;

    @Column(name = "provider_transaction_no", length = 64)
    private String providerTransactionNo;

    @Column(name = "completed_event_id", length = 128)
    private String completedEventId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expired_at", nullable = false)
    private Instant expiredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
