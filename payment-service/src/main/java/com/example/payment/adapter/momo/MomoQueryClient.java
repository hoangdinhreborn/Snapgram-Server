package com.example.payment.adapter.momo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Goi MoMo /v2/gateway/api/query de doi chieu giao dich cho admin reconcile.
 * Request ky HMAC + kenh HTTPS — cung muc do tin cay nhu VNPay QueryDR.
 */
@Component
@Slf4j
public class MomoQueryClient {

    private final MomoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    @Autowired
    public MomoQueryClient(MomoProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, RestClient.create());
    }

    public MomoQueryClient(MomoProperties properties, ObjectMapper objectMapper, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClient;
    }

    /** Tra ve JSON response goc tu MoMo. Nem exception neu khong doi chieu duoc. */
    public Map<String, Object> queryTransaction(String orderId) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("MoMo is not configured (momo.partner-code/access-key/secret-key)");
        }
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of(
                "partnerCode", properties.getPartnerCode(),
                "requestId", requestId,
                "orderId", orderId,
                "lang", properties.getLang(),
                "signature", MomoSigner.hmacSha256(properties.getSecretKey(),
                        MomoSigner.queryRaw(properties.getAccessKey(), orderId,
                                properties.getPartnerCode(), requestId)));

        try {
            String json = restClient.post()
                    .uri(properties.getQueryUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            Map<String, Object> resp = objectMapper.readValue(json, new TypeReference<>() {
            });
            log.info("MoMo query orderId={} resultCode={} message={}", orderId,
                    resp.get("resultCode"), resp.get("message"));
            return resp;
        } catch (Exception e) {
            throw new IllegalStateException("MoMo query failed for order " + orderId + ": " + e.getMessage(), e);
        }
    }
}
