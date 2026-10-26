package com.example.payment.adapter.momo;

import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.port.PaymentGateway;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter MoMo Payment API v2.
 * createPayment: POST /v2/gateway/api/create -> payUrl.
 * verifyCallback: dung cho ca IPN (POST JSON) va return (GET query string).
 */
@Component
@Slf4j
public class MomoPaymentGateway implements PaymentGateway {

    public static final String PROVIDER = "MOMO";
    /** resultCode 0 = thanh cong — chuan hoa thanh "00" ma thanh cong chung. */
    private static final String SUCCESS_CODE = "00";

    private final MomoProperties properties;
    private final ObjectMapper objectMapper;
    private final MomoQueryClient queryClient;
    private final RestClient restClient;

    @Autowired
    public MomoPaymentGateway(MomoProperties properties, ObjectMapper objectMapper, MomoQueryClient queryClient) {
        this(properties, objectMapper, queryClient, buildRestClient(properties));
    }

    public MomoPaymentGateway(MomoProperties properties, ObjectMapper objectMapper,
                              MomoQueryClient queryClient, RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.queryClient = queryClient;
        this.restClient = restClient;
    }

    /** MoMo can it nhat 30s doc timeout — tranh dong ket qua khi MoMo tra cham. */
    private static RestClient buildRestClient(MomoProperties properties) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 30)));
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public String createPayment(CreatePaymentCommand command) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("MoMo is not configured (momo.partner-code/access-key/secret-key)");
        }
        // requestId String(50), orderId 32 ky tu da unique theo tung don
        String requestId = command.orderId();
        String amount = String.valueOf(command.amount());
        String signature = MomoSigner.createSignature(properties.getSecretKey(), properties.getAccessKey(),
                amount, properties.getExtraData(), properties.getIpnUrl(), command.orderId(),
                command.orderInfo(), properties.getPartnerCode(), properties.getReturnUrl(),
                requestId, properties.getRequestType());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", properties.getPartnerCode());
        body.put("requestId", requestId);
        body.put("orderId", command.orderId());
        // MoMo yeu cau amount la Long VND (khong x100 nhu VNPay)
        body.put("amount", command.amount());
        body.put("orderInfo", command.orderInfo());
        body.put("redirectUrl", properties.getReturnUrl());
        body.put("ipnUrl", properties.getIpnUrl());
        body.put("requestType", properties.getRequestType());
        body.put("extraData", properties.getExtraData());
        body.put("lang", properties.getLang());
        body.put("signature", signature);

        Map<String, Object> resp;
        try {
            String json = restClient.post()
                    .uri(properties.getCreateUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            resp = objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("MoMo createPayment failed for order "
                    + command.orderId() + ": " + e.getMessage(), e);
        }

        long resultCode = asLong(resp.get("resultCode"));
        if (resultCode != 0L) {
            log.warn("MoMo create rejected orderId={} resultCode={} message={}",
                    command.orderId(), resultCode, resp.get("message"));
            throw new IllegalStateException(
                    "MoMo createPayment failed: resultCode=" + resultCode + ", message=" + resp.get("message"));
        }
        String payUrl = asText(resp.get("payUrl"));
        if (payUrl == null || payUrl.isBlank()) {
            throw new IllegalStateException("MoMo createPayment returned no payUrl for order " + command.orderId());
        }
        return payUrl;
    }

    @Override
    public PaymentCallbackResult verifyCallback(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return PaymentCallbackResult.invalid("Missing callback params");
        }
        // Chan truoc callback cua merchant khac / env khac
        String partnerCode = params.get("partnerCode");
        if (partnerCode == null || !partnerCode.equals(properties.getPartnerCode())) {
            return PaymentCallbackResult.invalid("Partner code mismatch");
        }
        String orderId = params.get("orderId");
        if (orderId == null || orderId.isBlank()) {
            return PaymentCallbackResult.invalid("Missing orderId");
        }
        if (!MomoSigner.verifyCallback(params, properties.getAccessKey(), properties.getSecretKey())) {
            return PaymentCallbackResult.invalid("Invalid signature");
        }
        String resultCode = params.get("resultCode");
        if (resultCode == null || resultCode.isBlank()) {
            return PaymentCallbackResult.invalid("Missing resultCode");
        }
        long amount;
        try {
            amount = Long.parseLong(params.getOrDefault("amount", ""));
        } catch (NumberFormatException e) {
            return PaymentCallbackResult.invalid("Invalid amount");
        }

        return PaymentCallbackResult.valid(
                orderId,
                amount,
                "0".equals(resultCode) ? SUCCESS_CODE : resultCode,
                null, // MoMo khong co transactionStatus rieng
                params.get("transId"),
                params.get("payType"),
                parseResponseTime(params.get("responseTime")));
    }

    @Override
    public QueryResult queryTransaction(QueryCommand command) {
        return toQueryResult(command, queryClient.queryTransaction(command.orderId()));
    }

    /** Parse ket qua query thanh ket qua chuan hoa. */
    private QueryResult toQueryResult(QueryCommand command, Map<String, Object> resp) {
        String orderId = asText(resp.get("orderId"));
        if (!command.orderId().equals(orderId)) {
            throw new IllegalStateException("OrderId mismatch from MoMo");
        }
        long resultCode = asLong(resp.get("resultCode"));
        long transId = asLong(resp.get("transId"));
        long amount = asLong(resp.get("amount"));
        String message = asText(resp.get("message"));
        // resultCode 0 = GD thanh cong; 1000 = don da tao dang cho khach tra tien (san sandbox
        // tra 1000/transId=0 cho don pending) — van phai found de reconcile bao "chua thanh toan"
        // thay vi 404 "khong ton tai"; 1005 = khong ton tai het han.
        boolean success = resultCode == 0L;
        boolean found = success || transId > 0L || resultCode == 1000L;
        return new QueryResult(found, success,
                success ? SUCCESS_CODE : String.valueOf(resultCode),
                transId > 0 ? String.valueOf(transId) : null,
                asText(resp.get("payType")),
                parseResponseTime(asText(resp.get("responseTime"))),
                amount, message);
    }

    /** IPN ack cua MoMo: resultCode/message (khac format RspCode cua VNPay). */
    @Override
    public Map<String, String> ipnAck(IpnOutcome outcome) {
        return switch (outcome) {
            // 0 = da xu ly xong (gop ca da xu ly truoc do) de MoMo khong retry
            case HANDLED, ALREADY_CONFIRMED -> ack("0", "Confirm Success");
            // != 0 -> MoMo bao loi cho khach va retry
            case ORDER_NOT_FOUND, INVALID_SIGNATURE -> ack("9", outcome.name());
            case INVALID_AMOUNT -> ack("22", "Invalid amount");
        };
    }

    private static Map<String, String> ack(String resultCode, String message) {
        return Map.of("resultCode", resultCode, "message", message);
    }

    private static Instant parseResponseTime(String responseTime) {
        if (responseTime == null || responseTime.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(responseTime));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
