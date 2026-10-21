package com.example.payment;

import com.example.payment.adapter.momo.MomoPaymentGateway;
import com.example.payment.adapter.momo.MomoProperties;
import com.example.payment.adapter.momo.MomoQueryClient;
import com.example.payment.adapter.momo.MomoSigner;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.port.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test LIVE MoMo sandbox — chi chay khi set env (xem .env):
 *   MOMO_PARTNER_CODE / MOMO_ACCESS_KEY / MOMO_SECRET_KEY
 * Chay: mvn -o -pl payment-service test -Dtest=MomoSandboxLiveTest
 * (mac dinh khong co env thi bo qua, khong lam test offline loi mang).
 */
@EnabledIfEnvironmentVariable(named = "MOMO_PARTNER_CODE", matches = ".+")
class MomoSandboxLiveTest {

    private static final String ACCESS_KEY = System.getenv("MOMO_ACCESS_KEY");
    private static final String SECRET_KEY = System.getenv("MOMO_SECRET_KEY");

    private static MomoProperties properties() {
        MomoProperties properties = new MomoProperties();
        properties.setPartnerCode(System.getenv("MOMO_PARTNER_CODE"));
        properties.setAccessKey(ACCESS_KEY);
        properties.setSecretKey(SECRET_KEY);
        properties.setReturnUrl("https://example.com/api/payments/momo/return");
        properties.setIpnUrl("https://example.com/api/payments/momo/ipn");
        return properties;
    }

    private static MomoPaymentGateway gateway() {
        MomoProperties properties = properties();
        ObjectMapper mapper = new ObjectMapper();
        return new MomoPaymentGateway(properties, mapper, new MomoQueryClient(properties, mapper));
    }

    private static String createOrder(MomoPaymentGateway gateway, String orderId) {
        String url = gateway.createPayment(new PaymentGateway.CreatePaymentCommand(
                orderId, 50_000L, "Thanh toan BLUE_TICK", "127.0.0.1",
                Instant.now(), Instant.now().plusSeconds(900)));
        assertNotNull(url);
        return url;
    }

    @Test
    void createPayment_returnsRealSandboxPayUrl() {
        String orderId = "SGTEST" + System.currentTimeMillis();

        String payUrl = createOrder(gateway(), orderId);

        assertTrue(payUrl.startsWith("https://test-payment.momo.vn/"), payUrl);
        assertTrue(payUrl.contains("/gateway/"), payUrl);
    }

    @Test
    void query_pendingOrder_isFoundButNotSuccessful() {
        MomoPaymentGateway gateway = gateway();
        String orderId = "SGTESTQ" + System.currentTimeMillis();
        createOrder(gateway, orderId);

        var result = gateway.queryTransaction(
                new PaymentGateway.QueryCommand(orderId, "Thanh toan BLUE_TICK", Instant.now()));

        // resultCode 1000 = cho khach tra tien — ton tai nhung chua thanh cong
        assertTrue(result.found(), "don pending phai found, khong duoc 404");
        assertFalse(result.success());
        assertEquals("1000", result.responseCode());
        assertEquals(50_000L, result.amount());
    }

    @Test
    void query_unknownOrder_notFound() {
        var result = gateway().queryTransaction(new PaymentGateway.QueryCommand(
                "SGKHONGTONTAI" + System.currentTimeMillis(), "khong co", Instant.now()));

        assertFalse(result.found());
        assertFalse(result.success());
    }

    @Test
    void verifyCallback_roundTripWithRealSandboxKeys() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("partnerCode", System.getenv("MOMO_PARTNER_CODE"));
        params.put("orderId", "SGTESTCB" + System.currentTimeMillis());
        params.put("requestId", "req-1");
        params.put("amount", "50000");
        params.put("orderInfo", "Thanh toan BLUE_TICK");
        params.put("orderType", "momo_wallet");
        params.put("transId", "4088878653");
        params.put("resultCode", "0");
        params.put("message", "Thành công.");
        params.put("payType", "qr");
        params.put("responseTime", "1721720663942");
        params.put("extraData", "");
        params.put("signature", MomoSigner.hmacSha256(SECRET_KEY, MomoSigner.callbackRaw(params, ACCESS_KEY)));

        PaymentCallbackResult valid = gateway().verifyCallback(params);
        assertTrue(valid.signatureValid(), valid.error());
        assertEquals("00", valid.responseCode());
        assertEquals("4088878653", valid.transactionNo());

        params.put("partnerCode", "MOMOKHAC"); // env khac khong duoc chap nhan
        assertFalse(gateway().verifyCallback(params).signatureValid());
    }
}
