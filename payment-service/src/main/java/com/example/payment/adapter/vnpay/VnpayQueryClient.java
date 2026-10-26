package com.example.payment.adapter.vnpay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Goi VNPay QueryDR (merchant_webapi/api/transaction) de doi chieu giao dich.
 * Request duoc ky HMAC (VNPay xac thuc) + kenh HTTPS — du tin cay cho admin reconcile.
 */
@Component
@Slf4j
public class VnpayQueryClient {

    private final VnpayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public VnpayQueryClient(VnpayProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, buildRestClient());
    }

    /** QueryDR la request admin doi soat — dat timeout de khong treo request vo han. */
    private static RestClient buildRestClient() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return RestClient.builder().requestFactory(factory).build();
    }

    public VnpayQueryClient(VnpayProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /** Tra ve map response tu VNPay. Nem exception neu khong doi chieu duoc. */
    public Map<String, String> queryTransaction(String orderId, String orderInfo, Instant transactionDate) {
        String requestId = VnpayPaymentGateway.formatPayDate(Instant.now())
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String txnDate = VnpayPaymentGateway.formatPayDate(transactionDate);
        String createDate = VnpayPaymentGateway.formatPayDate(Instant.now());

        Map<String, String> body = new HashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", properties.getVersion());
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", properties.getTmnCode());
        body.put("vnp_TxnRef", orderId);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", txnDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", "127.0.0.1");

        String raw = String.join("|", requestId, properties.getVersion(), "querydr",
                properties.getTmnCode(), orderId, txnDate, createDate, "127.0.0.1", orderInfo);
        body.put("vnp_SecureHash", VnpaySigner.hmacSha512(properties.getHashSecret(), raw));

        Map<String, String> resp;
        try {
            String json = restClient.post()
                    .uri(properties.getApiUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            resp = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("QueryDR failed for order " + orderId + ": " + e.getMessage(), e);
        }

        // VNPay ky ca response QueryDR — co hash thi phai khop, sai thi khong tin du lieu doi soat.
        String responseHash = resp.get("vnp_SecureHash");
        if (responseHash == null || responseHash.isBlank()) {
            log.warn("QueryDR response has no vnp_SecureHash for order {} — skipping signature check", orderId);
        } else if (!VnpaySigner.verifyQueryDrResponse(resp, properties.getTmnCode(),
                properties.getHashSecret())) {
            throw new IllegalStateException("Invalid QueryDR response signature for order " + orderId);
        }

        log.info("QueryDR orderId={} responseCode={} txnStatus={}", orderId,
                resp.get("vnp_ResponseCode"), resp.get("vnp_TransactionStatus"));
        return resp;
    }
}
