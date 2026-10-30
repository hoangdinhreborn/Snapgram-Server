package com.example.payment;

import com.example.payment.adapter.vnpay.VnpayPaymentGateway;
import com.example.payment.adapter.vnpay.VnpayProperties;
import com.example.payment.adapter.vnpay.VnpayQueryClient;
import com.example.payment.adapter.vnpay.VnpaySigner;
import com.example.payment.dto.payment.PaymentCallbackResult;
import com.example.payment.port.PaymentGateway;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Test adapter VNPay: URL/create, verify callback (ca 2 ma success), QueryDR mapping, ack. */
class VnpayPaymentUnitTest {

    private static final String SECRET = "VNPAY_TEST_SECRET";
    private static final Instant CREATE_AT = Instant.parse("2026-10-19T05:34:56Z");

    private static VnpayProperties properties() {
        VnpayProperties properties = new VnpayProperties();
        properties.setUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setApiUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
        properties.setTmnCode("TMNTEST");
        properties.setHashSecret(SECRET);
        properties.setReturnUrl("http://localhost:8080/api/payments/vnpay/return");
        return properties;
    }

    private static VnpayPaymentGateway gateway(VnpayProperties properties) {
        return new VnpayPaymentGateway(properties, mock(VnpayQueryClient.class));
    }

    private static PaymentGateway.CreatePaymentCommand command() {
        return new PaymentGateway.CreatePaymentCommand(
                "abc123", 199_000L, "Thanh toan BLUE_TICK", "127.0.0.1",
                CREATE_AT, CREATE_AT.plusSeconds(900));
    }

    private static Map<String, String> queryParams(String url) {
        Map<String, String> params = new LinkedHashMap<>();
        String query = url.substring(url.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            params.put(URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return params;
    }

    private static Map<String, String> callbackParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_TmnCode", "TMNTEST");
        params.put("vnp_TxnRef", "abc123");
        params.put("vnp_Amount", "19900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "123456");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_PayDate", "20261019123456");
        params.put("vnp_OrderInfo", "Thanh toan BLUE_TICK");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));
        return params;
    }

    @Test
    void createPayment_buildsSignedUrl_withAmountTimes100() {
        String url = gateway(properties()).createPayment(command());

        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        Map<String, String> params = queryParams(url);
        assertEquals("19900000", params.get("vnp_Amount"));
        assertEquals("TMNTEST", params.get("vnp_TmnCode"));
        assertEquals("abc123", params.get("vnp_TxnRef"));
        assertEquals("2.1.0", params.get("vnp_Version"));
        assertEquals("pay", params.get("vnp_Command"));
        assertEquals("VND", params.get("vnp_CurrCode"));
        assertEquals("127.0.0.1", params.get("vnp_IpAddr"));
        assertEquals("20261019123456", params.get("vnp_CreateDate"));
        assertEquals("20261019124956", params.get("vnp_ExpireDate"));
        assertEquals("http://localhost:8080/api/payments/vnpay/return", params.get("vnp_ReturnUrl"));
        assertFalse(params.containsKey("vnp_SecureHashType"));

        // Chu ky phai khop khi tinh lai tu chinh cac tham so da gui
        String expected = VnpaySigner.sign(VnpaySigner.signable(params), SECRET);
        assertEquals(expected, params.get("vnp_SecureHash"));
    }

    @Test
    void createPayment_withoutConfig_throwsBeforeCallingProvider() {
        VnpayProperties properties = properties();
        properties.setHashSecret(" ");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> gateway(properties).createPayment(command()));
        assertTrue(ex.getMessage().contains("VNPay is not configured"));
    }

    @Test
    void verifyCallback_validSuccess_requiresResponseAndTransactionStatus() {
        PaymentCallbackResult result = gateway(properties()).verifyCallback(callbackParams());

        assertTrue(result.signatureValid(), result.error());
        assertEquals("abc123", result.orderId());
        assertEquals(199_000L, result.amount());
        assertEquals("00", result.responseCode());
        assertEquals("00", result.transactionStatus());
        assertTrue(result.isSuccessCode());
    }

    @Test
    void verifyCallback_responseCode00_butTransactionStatusFailed_isNotSuccess() {
        Map<String, String> params = callbackParams();
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        PaymentCallbackResult result = gateway(properties()).verifyCallback(params);

        assertTrue(result.signatureValid(), result.error());
        assertFalse(result.isSuccessCode(), "VNPay chi success khi ca 2 ma = 00");
    }

    @Test
    void verifyCallback_tamperedAmount_invalidSignature() {
        Map<String, String> params = callbackParams();
        params.put("vnp_Amount", "1000"); // khong ky lai

        assertFalse(gateway(properties()).verifyCallback(params).signatureValid());
    }

    @Test
    void verifyCallback_foreignTmnCode_withValidSignature_isRejected() {
        Map<String, String> params = callbackParams();
        params.put("vnp_TmnCode", "OTHER");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        PaymentCallbackResult result = gateway(properties()).verifyCallback(params);

        assertFalse(result.signatureValid());
        assertEquals("Merchant code mismatch", result.error());
    }

    @Test
    void verifyCallback_missingTxnRef_invalid() {
        Map<String, String> params = callbackParams();
        params.remove("vnp_TxnRef");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        assertEquals("Missing vnp_TxnRef", gateway(properties()).verifyCallback(params).error());
    }

    @Test
    void verifyCallback_missingResponseCode_invalid() {
        Map<String, String> params = callbackParams();
        params.remove("vnp_ResponseCode");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        assertEquals("Missing vnp_ResponseCode", gateway(properties()).verifyCallback(params).error());
    }

    @Test
    void verifyCallback_missingTransactionStatus_invalid() {
        Map<String, String> params = callbackParams();
        params.remove("vnp_TransactionStatus");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        assertEquals("Missing vnp_TransactionStatus", gateway(properties()).verifyCallback(params).error());
    }

    @Test
    void verifyCallback_unparseableAmount_invalid() {
        Map<String, String> params = callbackParams();
        params.put("vnp_Amount", "abc");
        params.put("vnp_SecureHash", VnpaySigner.sign(VnpaySigner.signable(params), SECRET));

        PaymentCallbackResult result = gateway(properties()).verifyCallback(params);
        assertFalse(result.signatureValid());
        assertEquals("Invalid vnp_Amount", result.error());
    }

    @Test
    void verifyQueryDrResponse_usesPipeSeparatedFixedFieldOrder() {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "req1");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "QueryDR Success");
        response.put("vnp_TxnRef", "abc123");
        response.put("vnp_Amount", "19900000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20261019123456");
        response.put("vnp_TransactionNo", "999");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", "00");
        response.put("vnp_OrderInfo", "Thanh toan BLUE_TICK");
        response.put("vnp_SecureHash", VnpaySigner.hmacSha512(SECRET,
                VnpaySigner.queryDrResponseRaw(response, "TMNTEST")));

        assertEquals("req1|querydr|00|QueryDR Success|TMNTEST|abc123|19900000|NCB|"
                        + "20261019123456|999|01|00|Thanh toan BLUE_TICK||",
                VnpaySigner.queryDrResponseRaw(response, "TMNTEST"));
        assertTrue(VnpaySigner.verifyQueryDrResponse(response, "TMNTEST", SECRET));

        response.put("vnp_Amount", "1"); // sua du lieu sau khi ky
        assertFalse(VnpaySigner.verifyQueryDrResponse(response, "TMNTEST", SECRET));

        response.remove("vnp_SecureHash");
        assertFalse(VnpaySigner.verifyQueryDrResponse(response, "TMNTEST", SECRET));
    }

    @Test
    void queryTransaction_success_mapsQueryDrResponse() {
        VnpayQueryClient client = mock(VnpayQueryClient.class);
        when(client.queryTransaction(anyString(), anyString(), any())).thenReturn(Map.of(
                "vnp_TxnRef", "abc123",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00",
                "vnp_TransactionNo", "999",
                "vnp_BankCode", "NCB",
                "vnp_PayDate", "20261019123456",
                "vnp_Amount", "19900000",
                "vnp_Message", "ok"));
        VnpayPaymentGateway gateway = new VnpayPaymentGateway(properties(), client);

        PaymentGateway.QueryResult result = gateway.queryTransaction(
                new PaymentGateway.QueryCommand("abc123", "Thanh toan", CREATE_AT));

        assertTrue(result.found());
        assertTrue(result.success());
        assertEquals("00", result.responseCode());
        assertEquals("999", result.transactionNo());
        assertEquals("NCB", result.channel());
        assertEquals(CREATE_AT, result.payDate());
        assertEquals(199_000L, result.amount());
    }

    @Test
    void queryTransaction_notFound_whenResponseCodeNotSuccess() {
        VnpayQueryClient client = mock(VnpayQueryClient.class);
        when(client.queryTransaction(anyString(), anyString(), any())).thenReturn(Map.of(
                "vnp_TxnRef", "abc123",
                "vnp_ResponseCode", "91",
                "vnp_Message", "Transaction not found"));
        VnpayPaymentGateway gateway = new VnpayPaymentGateway(properties(), client);

        PaymentGateway.QueryResult result = gateway.queryTransaction(
                new PaymentGateway.QueryCommand("abc123", "Thanh toan", CREATE_AT));

        assertFalse(result.found());
        assertFalse(result.success());
        assertEquals("91", result.responseCode());
    }

    @Test
    void queryTransaction_transactionStatusFailed_isFoundButNotSuccess() {
        VnpayQueryClient client = mock(VnpayQueryClient.class);
        when(client.queryTransaction(anyString(), anyString(), any())).thenReturn(Map.of(
                "vnp_TxnRef", "abc123",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "02",
                "vnp_Amount", "19900000"));
        VnpayPaymentGateway gateway = new VnpayPaymentGateway(properties(), client);

        PaymentGateway.QueryResult result = gateway.queryTransaction(
                new PaymentGateway.QueryCommand("abc123", "Thanh toan", CREATE_AT));

        assertTrue(result.found());
        assertFalse(result.success());
    }

    @Test
    void queryTransaction_txnRefMismatch_throws() {
        VnpayQueryClient client = mock(VnpayQueryClient.class);
        when(client.queryTransaction(anyString(), anyString(), any())).thenReturn(Map.of(
                "vnp_TxnRef", "other",
                "vnp_ResponseCode", "00",
                "vnp_TransactionStatus", "00"));
        VnpayPaymentGateway gateway = new VnpayPaymentGateway(properties(), client);

        assertThrows(IllegalStateException.class, () -> gateway.queryTransaction(
                new PaymentGateway.QueryCommand("abc123", "Thanh toan", CREATE_AT)));
    }

    @Test
    void ipnAck_mapsAllOutcomesToRspCode() {
        VnpayPaymentGateway gateway = gateway(properties());

        assertEquals("00", gateway.ipnAck(PaymentGateway.IpnOutcome.HANDLED).get("RspCode"));
        assertEquals("01", gateway.ipnAck(PaymentGateway.IpnOutcome.ORDER_NOT_FOUND).get("RspCode"));
        assertEquals("02", gateway.ipnAck(PaymentGateway.IpnOutcome.ALREADY_CONFIRMED).get("RspCode"));
        assertEquals("04", gateway.ipnAck(PaymentGateway.IpnOutcome.INVALID_AMOUNT).get("RspCode"));
        assertEquals("97", gateway.ipnAck(PaymentGateway.IpnOutcome.INVALID_SIGNATURE).get("RspCode"));
    }
}
