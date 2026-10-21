package com.example.payment;

import com.example.payment.adapter.momo.MomoPaymentGateway;
import com.example.payment.adapter.momo.MomoProperties;
import com.example.payment.adapter.momo.MomoQueryClient;
import com.example.payment.adapter.momo.MomoSigner;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.port.PaymentGateway;
import com.example.payment.port.PaymentGateway.IpnOutcome;
import com.example.payment.port.PaymentGateway.QueryCommand;
import com.example.payment.port.PaymentGatewayRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MomoPaymentUnitTest {

    private static final String ACCESS_KEY = "F8BBA842ECF85";
    private static final String SECRET_KEY = "K951B6PE1waDMi640xX08PD3vg6EkVlz";

    private static MomoProperties properties() {
        MomoProperties properties = new MomoProperties();
        properties.setPartnerCode("MOMO");
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setReturnUrl("http://localhost:8080/api/payments/momo/return");
        properties.setIpnUrl("http://localhost:8087/api/payments/momo/ipn");
        return properties;
    }

    private static MomoPaymentGateway gateway() {
        return new MomoPaymentGateway(properties(), new ObjectMapper(), null, null);
    }

    private static MomoPaymentGateway gatewayReturning(Map<String, Object> queryResponse) {
        MomoQueryClient queryClient = mock(MomoQueryClient.class);
        when(queryClient.queryTransaction("order123")).thenReturn(queryResponse);
        return new MomoPaymentGateway(properties(), new ObjectMapper(), queryClient, null);
    }

    /** Cac field ky theo thu tu CO DINH cua docs MoMo, khong sort alphabet nhu VNPay. */
    private static Map<String, String> ipnParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("partnerCode", "MOMO");
        params.put("orderId", "order123");
        params.put("requestId", "req123");
        params.put("amount", "199000");
        params.put("orderInfo", "Thanh toan BLUE_TICK");
        params.put("orderType", "momo_wallet");
        params.put("transId", "4088878653");
        params.put("resultCode", "0");
        params.put("message", "Successful.");
        params.put("payType", "qr");
        params.put("responseTime", "1721720663942");
        params.put("extraData", "");
        return params;
    }

    @Test
    void createSignature_matchesOfficialSample() {
        String signature = MomoSigner.createSignature(SECRET_KEY, ACCESS_KEY, "50000", "",
                "http://localhost:8087/api/payments/momo/ipn",
                "order123", "Thanh toan BLUE_TICK", "MOMO",
                "http://localhost:8080/api/payments/momo/return",
                "req123", "captureWallet");
        assertEquals("d0036abdd67763f7ede492151e3ce8827698233d2ca9ff7b9f30cbce1f6671cd", signature);
    }

    @Test
    void callbackRaw_fixedFieldOrder() {
        Map<String, String> params = ipnParams();
        String raw = MomoSigner.callbackRaw(params, ACCESS_KEY);
        assertEquals("accessKey=F8BBA842ECF85&amount=199000&extraData=&message=Successful."
                + "&orderId=order123&orderInfo=Thanh toan BLUE_TICK&orderType=momo_wallet"
                + "&partnerCode=MOMO&payType=qr&requestId=req123&responseTime=1721720663942"
                + "&resultCode=0&transId=4088878653", raw);
        assertEquals("f0a292a4eea783cbd65254455bab19f796ebbb0a915d04d9aee79658e5100aea",
                MomoSigner.hmacSha256(SECRET_KEY, raw));
    }

    @Test
    void verifyCallback_validSignature_normalizesSuccessCode() {
        Map<String, String> params = ipnParams();
        params.put("signature", MomoSigner.hmacSha256(SECRET_KEY, MomoSigner.callbackRaw(params, ACCESS_KEY)));

        PaymentCallbackResult result = gateway().verifyCallback(params);

        assertTrue(result.signatureValid(), result.error());
        assertEquals("order123", result.orderId());
        assertEquals(199_000L, result.amount());
        // resultCode 0 -> "00" cho frontend/vnpay isSuccessCode
        assertEquals("00", result.responseCode());
        assertTrue(result.isSuccessCode());
        assertEquals("4088878653", result.transactionNo());
        assertEquals("qr", result.bankCode());
        assertEquals(Instant.ofEpochMilli(1721720663942L), result.payDate());
    }

    @Test
    void verifyCallback_rejectsTamperedOrForeignPayload() {
        Map<String, String> tampered = ipnParams();
        tampered.put("signature", MomoSigner.hmacSha256(SECRET_KEY, MomoSigner.callbackRaw(tampered, ACCESS_KEY)));
        tampered.put("amount", "1"); // doi so tien sau khi ky
        assertFalse(gateway().verifyCallback(tampered).signatureValid());

        Map<String, String> wrongMerchant = ipnParams();
        wrongMerchant.put("partnerCode", "OTHER");
        wrongMerchant.put("signature", MomoSigner.hmacSha256(SECRET_KEY, MomoSigner.callbackRaw(wrongMerchant, ACCESS_KEY)));
        assertFalse(gateway().verifyCallback(wrongMerchant).signatureValid());

        assertFalse(gateway().verifyCallback(Map.of()).signatureValid());
    }

    @Test
    void queryResult_mapsMoMoResultCodes() {
        QueryCommand command = new QueryCommand("order123", "Thanh toan BLUE_TICK", Instant.now());

        var ok = gatewayReturning(Map.of(
                "orderId", "order123", "resultCode", 0, "transId", 4088878653L,
                "amount", 199000, "payType", "qr", "responseTime", 1721720663942L,
                "message", "Thành công.")).queryTransaction(command);
        assertTrue(ok.found());
        assertTrue(ok.success());
        assertEquals("00", ok.responseCode());
        assertEquals(199_000L, ok.amount());

        var failed = gatewayReturning(Map.of(
                "orderId", "order123", "resultCode", 9, "transId", 4088878653L,
                "amount", 199000, "message", "System error")).queryTransaction(command);
        assertTrue(failed.found());
        assertFalse(failed.success());

        var missing = gatewayReturning(Map.of(
                "orderId", "order123", "resultCode", 9, "transId", 0, "message", "Not found"))
                .queryTransaction(command);
        assertFalse(missing.found());

        // San sandbox tra 1000 cho don pending, 1005 cho don khong ton tai (goi that 2026-10-17)
        var pending = gatewayReturning(Map.of(
                "orderId", "order123", "resultCode", 1000, "transId", 0, "amount", 50000,
                "message", "Giao dịch đã được khởi tạo, chờ người dùng xác nhận thanh toán."))
                .queryTransaction(command);
        assertTrue(pending.found());
        assertFalse(pending.success());
        assertEquals("1000", pending.responseCode());

        var expired = gatewayReturning(Map.of(
                "orderId", "order123", "resultCode", 1005, "transId", 0,
                "message", "Transaction expired or did not exist.")).queryTransaction(command);
        assertFalse(expired.found());

        MomoPaymentGateway mismatched = gatewayReturning(Map.of("orderId", "other", "resultCode", 0));
        assertThrows(IllegalStateException.class, () -> mismatched.queryTransaction(command));
    }

    @Test
    void ipnAck_outcomeCodes() {
        MomoPaymentGateway gw = gateway();
        assertEquals("0", gw.ipnAck(IpnOutcome.HANDLED).get("resultCode"));
        assertEquals("0", gw.ipnAck(IpnOutcome.ALREADY_CONFIRMED).get("resultCode"));
        assertEquals("9", gw.ipnAck(IpnOutcome.ORDER_NOT_FOUND).get("resultCode"));
        assertEquals("22", gw.ipnAck(IpnOutcome.INVALID_AMOUNT).get("resultCode"));
    }

    @Test
    void registry_resolvesByProvider_andRejectsUnknown() {
        PaymentGateway momo = mock(PaymentGateway.class);
        when(momo.provider()).thenReturn("MOMO");
        PaymentGatewayRegistry registry = new PaymentGatewayRegistry(List.of(momo));

        assertSame(momo, registry.get("MOMO"));
        assertSame(momo, registry.get("momo"));
        assertThrows(IllegalArgumentException.class, () -> registry.get("PAYPAL"));
        assertThrows(IllegalArgumentException.class, () -> registry.get(" "));
    }

    /** Thieu key env -> loi ro rang tai backend thay vi goi API MoMo roi moi that bai. */
    @Test
    void createPayment_withoutConfig_throwsClearError() {
        MomoPaymentGateway gw = new MomoPaymentGateway(new MomoProperties(), new ObjectMapper(), null, null);
        PaymentGateway.CreatePaymentCommand command = new PaymentGateway.CreatePaymentCommand(
                "order123", 199_000L, "Thanh toan BLUE_TICK",
                "127.0.0.1", Instant.now(), Instant.now().plusSeconds(900));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> gw.createPayment(command));
        assertTrue(ex.getMessage().contains("not configured"), ex.getMessage());
    }
}
