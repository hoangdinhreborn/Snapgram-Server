package com.example.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh scheduler tu dong doi chieu don PENDING voi provider.
 * Mac dinh chi MoMo vi public sandbox partner khong gui IPN on dinh.
 */
@Data
@ConfigurationProperties(prefix = "payment.reconcile")
public class PaymentReconcileProperties {

    private String provider = "MOMO";
    /** Bo qua don vua tao de tranh query truoc khi khach kip thanh toan. */
    private long minAgeSeconds = 30;
    /** Van doi chieu don vua EXPIRED trong khoang nay de bat giao dich den tre. */
    private long expiredGraceSeconds = 900;
    private int batchSize = 20;
}
