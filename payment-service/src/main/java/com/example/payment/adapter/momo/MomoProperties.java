package com.example.payment.adapter.momo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cau hinh MoMo Payment API v2 (/v2/gateway/api/*).
 * Sandbox: https://test-payment.momo.vn — Production: https://payment.momo.vn
 */
@Data
@ConfigurationProperties(prefix = "momo")
public class MomoProperties {

    /** Base URL (khong trailing slash) cua MoMo Gateway. */
    private String baseUrl = "https://test-payment.momo.vn";
    private String partnerCode;
    private String accessKey;
    private String secretKey;
    /** MoMo redirect user ve day sau khi thanh toan (GET query string). */
    private String returnUrl;
    /** MoMo POST IPN server-to-server (JSON). */
    private String ipnUrl;
    /** captureWallet (hosted) | payWithMethod ... */
    private String requestType = "captureWallet";
    private String lang = "vi";
    private String extraData = "";
    /** MoMo yeu cau timeout toi thieu 30s cho moi request. */
    private int timeoutSeconds = 30;

    public String getCreateUrl() {
        return baseUrl + "/v2/gateway/api/create";
    }

    public String getQueryUrl() {
        return baseUrl + "/v2/gateway/api/query";
    }

    /** Chua du key -> createPayment se loi ro rang thay vi goi API loi. */
    public boolean isConfigured() {
        return notBlank(partnerCode) && notBlank(accessKey) && notBlank(secretKey);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
