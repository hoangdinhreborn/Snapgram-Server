package com.example.payment;

import com.example.payment.adapter.vnpay.VnpaySigner;
import com.example.payment.controller.VnpayCallbackController;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.AccessDeniedException;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.service.ConfigPaymentCatalog;
import com.example.payment.service.PaymentService;
import com.example.payment.service.PaymentStateMachine;
import com.example.payment.util.UserContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentUnitTest {

    @Test
    void catalog_blueTick_price() {
        var catalog = new ConfigPaymentCatalog();
        var product = catalog.getProduct("BLUE_TICK");
        assertEquals(199_000L, product.amount());
        assertEquals("VND", product.currency());
        assertThrows(IllegalArgumentException.class, () -> catalog.getProduct("UNKNOWN"));
    }

    @Test
    void signer_sortAndVerify() {
        Map<String, String> params = new TreeMap<>(Map.of(
                "vnp_Amount", "19900000",
                "vnp_TmnCode", "ABC",
                "vnp_TxnRef", "order1"));
        String secret = "secret";
        String hash = VnpaySigner.sign(params, secret);
        assertTrue(VnpaySigner.verify(params, hash, secret));
        assertFalse(VnpaySigner.verify(params, hash + "x", secret));
        assertFalse(VnpaySigner.verify(params, null, secret));
    }

    @Test
    void signer_spaceEncodedAsPlus_likeVnpaySample() {
        // Code mau VNPay: space -> '+'. Doi sang %20 se lech chu ky.
        assertEquals("vnp_OrderInfo=Thanh+toan",
                VnpaySigner.buildHashData(Map.of("vnp_OrderInfo", "Thanh toan")));
    }

    @Test
    void stateMachine_blocksIllegal() {
        var sm = new PaymentStateMachine();
        assertTrue(sm.canTransition(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        assertTrue(sm.canTransition(PaymentStatus.PENDING, PaymentStatus.FAILED));
        assertFalse(sm.canTransition(PaymentStatus.SUCCESS, PaymentStatus.PENDING));
        assertFalse(sm.canTransition(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        assertFalse(sm.canTransition(PaymentStatus.FAILED, PaymentStatus.SUCCESS));
        assertThrows(IllegalStateException.class,
                () -> sm.checkTransition(PaymentStatus.SUCCESS, PaymentStatus.PENDING));
    }

    @Test
    void expireOverdue_delegatesBulkUpdate() {
        PaymentOrderRepository repo = mock(PaymentOrderRepository.class);
        when(repo.expireOverdue(eq(PaymentStatus.PENDING), eq(PaymentStatus.EXPIRED), any(Instant.class)))
                .thenReturn(2);
        PaymentService service = new PaymentService(repo, null, null, null, null,
                new PaymentStateMachine(), null, null);
        assertEquals(2, service.expireOverduePayments());
    }

    @Test
    void requireAdmin_blocksNonAdmin() {
        withRequest("USER", () -> assertThrows(AccessDeniedException.class, UserContext::requireAdmin));
        withRequest("ADMIN", UserContext::requireAdmin);
        withRequest("ROLE_ADMIN", UserContext::requireAdmin);
    }

    @Test
    void vnpayReturn_redirectsToFrontend() {
        PaymentService service = mock(PaymentService.class);
        when(service.previewReturn(any()))
                .thenReturn(PaymentCallbackResult.valid("order123", 199_000L, "00",
                        "txn1", "NCB", "TMN", Instant.now()));
        VnpayCallbackController controller = new VnpayCallbackController(service);
        ReflectionTestUtils.setField(controller, "frontendResultUrl", "http://localhost:3000/payment/result");

        var response = controller.vnpayReturn(Map.of("vnp_TxnRef", "order123"));
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        String location = String.valueOf(response.getHeaders().getLocation());
        assertTrue(location.startsWith("http://localhost:3000/payment/result?"));
        assertTrue(location.contains("orderId=order123"));
        assertTrue(location.contains("responseCode=00"));
    }

    private static void withRequest(String roles, Runnable action) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-User-Id", UUID.randomUUID().toString());
        req.addHeader("X-User-Roles", roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
        try {
            action.run();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
