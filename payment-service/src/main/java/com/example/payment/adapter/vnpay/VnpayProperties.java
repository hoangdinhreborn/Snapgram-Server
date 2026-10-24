package com.example.payment.adapter.vnpay;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {
    private String url;
    private String apiUrl;
    private String tmnCode;
    private String hashSecret;
    private String returnUrl;
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private String currency = "VND";
    private String locale = "vn";

    /** Thieu tmnCode/hashSecret -> createPayment loi ro rang thay vi goi VNPay bang chu ky rong. */
    public boolean isConfigured() {
        return notBlank(tmnCode) && notBlank(hashSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
