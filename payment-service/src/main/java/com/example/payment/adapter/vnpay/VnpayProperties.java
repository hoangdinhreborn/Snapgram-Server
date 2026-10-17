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
    private String ipnUrl;
    private String version = "2.1.0";
    private String command = "pay";
    private String orderType = "other";
    private String currency = "VND";
    private String locale = "vn";
}
