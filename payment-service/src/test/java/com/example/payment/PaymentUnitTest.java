package com.example.payment;

import com.example.payment.adapter.vnpay.VnpaySigner;
import com.example.payment.controller.VnpayCallbackController;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.AccessDeniedException;
import com.example.payment.port.PaymentGateway;
import com.example.payment.port.PaymentGateway.IpnOutcome;
import com.example.payment.port.PaymentGatewayRegistry;
import com.example.payment.repository.PaymentEventLogRepository;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.repository.PaymentOutboxRepository;
import com.example.payment.service.ConfigPaymentCatalog;
import com.example.payment.service.PaymentService;
import com.example.payment.service.PaymentStateMachine;
import com.example.payment.util.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        // IPN den tre sau khi scheduler EXPIRED, hoac khach tra tien o lan sau:
        // da thu tien thi phai cho phep cap quyen.
        assertTrue(sm.canTransition(PaymentStatus.EXPIRED, PaymentStatus.SUCCESS));
        assertTrue(sm.canTransition(PaymentStatus.FAILED, PaymentStatus.SUCCESS));
        assertTrue(sm.canTransition(PaymentStatus.CANCELLED, PaymentStatus.SUCCESS));
        assertTrue(sm.canTransition(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED));
        assertFalse(sm.canTransition(PaymentStatus.REFUNDED, PaymentStatus.SUCCESS));
        assertFalse(sm.canTransition(PaymentStatus.SUCCESS, PaymentStatus.PENDING));
        assertFalse(sm.canTransition(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        assertFalse(sm.canTransition(PaymentStatus.EXPIRED, PaymentStatus.FAILED));
        assertThrows(IllegalStateException.class,
                () -> sm.checkTransition(PaymentStatus.SUCCESS, PaymentStatus.PENDING));
    }

    @Test
    void ipn_successOnExpiredOrder_stillGrantsEntitlement() {
        PaymentOrder order = order(PaymentStatus.EXPIRED);
        var fixture = ipnFixture(order, PaymentCallbackResult.valid(
                "order1", 199_000L, "00", null, "txn1", "web", Instant.now()));
        when(fixture.eventLogRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any())).thenReturn(1);

        Map<String, String> ack = fixture.service.handleIpn("MOMO", Map.of());

        assertEquals(PaymentStatus.SUCCESS, order.getStatus());
        assertEquals("txn1", order.getProviderTransactionNo());
        assertEquals("0", ack.get("resultCode"));
        verify(fixture.outboxRepository).insertIfAbsent(any(), eq("order1"), eq("momo:order1:txn1"), any());
        ArgumentCaptor<IpnOutcome> outcome = ArgumentCaptor.forClass(IpnOutcome.class);
        verify(fixture.gateway).ipnAck(outcome.capture());
        assertEquals(IpnOutcome.HANDLED, outcome.getValue());
    }

    @Test
    void ipn_successOnFailedOrder_marksSuccess() {
        PaymentOrder order = order(PaymentStatus.FAILED);
        var fixture = ipnFixture(order, PaymentCallbackResult.valid(
                "order1", 199_000L, "00", null, "txn2", "web", Instant.now()));
        when(fixture.eventLogRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any())).thenReturn(1);

        fixture.service.handleIpn("MOMO", Map.of());

        assertEquals(PaymentStatus.SUCCESS, order.getStatus());
    }

    @Test
    void ipn_failureOnExpiredOrder_keepsStatus() {
        PaymentOrder order = order(PaymentStatus.EXPIRED);
        var fixture = ipnFixture(order, PaymentCallbackResult.valid(
                "order1", 199_000L, "1006", null, "txn3", "web", Instant.now()));

        Map<String, String> ack = fixture.service.handleIpn("MOMO", Map.of());

        assertEquals(PaymentStatus.EXPIRED, order.getStatus());
        assertEquals("0", ack.get("resultCode"));
        ArgumentCaptor<IpnOutcome> outcome = ArgumentCaptor.forClass(IpnOutcome.class);
        verify(fixture.gateway).ipnAck(outcome.capture());
        assertEquals(IpnOutcome.ALREADY_CONFIRMED, outcome.getValue());
    }

    @Test
    void ipn_duplicateEventLog_isIdempotent() {
        PaymentOrder order = order(PaymentStatus.PENDING);
        var fixture = ipnFixture(order, PaymentCallbackResult.valid(
                "order1", 199_000L, "00", null, "txn1", "web", Instant.now()));
        when(fixture.eventLogRepository.insertIfAbsent(any(), anyString(), anyString(), anyString(),
                anyString(), anyString(), any())).thenReturn(0);

        Map<String, String> ack = fixture.service.handleIpn("MOMO", Map.of());

        assertEquals("0", ack.get("resultCode"));
        verify(fixture.outboxRepository, never()).insertIfAbsent(any(), anyString(), anyString(), any());
    }

    private record IpnFixture(PaymentService service, PaymentGateway gateway,
                              PaymentEventLogRepository eventLogRepository,
                              PaymentOutboxRepository outboxRepository) {
    }

    private static IpnFixture ipnFixture(PaymentOrder order, PaymentCallbackResult callback) {
        PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
        PaymentEventLogRepository eventLogRepository = mock(PaymentEventLogRepository.class);
        PaymentOutboxRepository outboxRepository = mock(PaymentOutboxRepository.class);
        PaymentGatewayRegistry registry = mock(PaymentGatewayRegistry.class);
        PaymentGateway gateway = mock(PaymentGateway.class);

        when(orderRepository.findByOrderIdForUpdate(order.getOrderId())).thenReturn(Optional.of(order));
        when(registry.get("MOMO")).thenReturn(gateway);
        when(gateway.verifyCallback(any())).thenReturn(callback);
        when(gateway.ipnAck(any())).thenReturn(Map.of("resultCode", "0"));

        PaymentService service = new PaymentService(orderRepository, eventLogRepository, outboxRepository,
                null, registry, new PaymentStateMachine());
        return new IpnFixture(service, gateway, eventLogRepository, outboxRepository);
    }

    private static PaymentOrder order(PaymentStatus status) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderId("order1");
        order.setProvider("MOMO");
        order.setAmount(199_000L);
        order.setStatus(status);
        return order;
    }

    @Test
    void expireOverdue_delegatesBulkUpdate() {
        PaymentOrderRepository repo = mock(PaymentOrderRepository.class);
        when(repo.expireOverdue(eq(PaymentStatus.PENDING), eq(PaymentStatus.EXPIRED), any(Instant.class)))
                .thenReturn(2);
        PaymentService service = new PaymentService(repo, null, null, null, null,
                new PaymentStateMachine());
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
        when(service.previewReturn(eq("VNPAY"), any()))
                .thenReturn(PaymentCallbackResult.valid("order123", 199_000L, "00", null,
                        "txn1", "NCB", Instant.now()));
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
