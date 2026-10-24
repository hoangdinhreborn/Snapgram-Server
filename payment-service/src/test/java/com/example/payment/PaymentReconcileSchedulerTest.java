package com.example.payment;

import com.example.payment.config.PaymentReconcileProperties;
import com.example.payment.config.PaymentReconcileScheduler;
import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentReconcileSchedulerTest {

    private final PaymentService paymentService = mock(PaymentService.class);
    private final PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
    private final PaymentReconcileProperties properties = new PaymentReconcileProperties();
    private PaymentReconcileScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties.setProvider("MOMO");
        properties.setMinAgeSeconds(30);
        properties.setExpiredGraceSeconds(900);
        properties.setBatchSize(20);
        scheduler = new PaymentReconcileScheduler(paymentService, orderRepository, properties);
    }

    @Test
    void reconcilesEachPendingOrder() {
        when(orderRepository.findReconcileCandidates(
                eq("MOMO"), eq(PaymentStatus.PENDING), eq(PaymentStatus.EXPIRED),
                any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(order("order1"), order("order2")));

        scheduler.reconcilePendingPayments();

        verify(paymentService).reconcile("order1");
        verify(paymentService).reconcile("order2");
    }

    @Test
    void continuesAfterOneOrderFails() {
        when(orderRepository.findReconcileCandidates(
                eq("MOMO"), eq(PaymentStatus.PENDING), eq(PaymentStatus.EXPIRED),
                any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(order("order1"), order("order2")));
        when(paymentService.reconcile("order1")).thenThrow(new IllegalStateException("pending"));

        scheduler.reconcilePendingPayments();

        verify(paymentService).reconcile("order1");
        verify(paymentService).reconcile("order2");
    }

    @Test
    void noCandidates_doesNotCallReconcile() {
        when(orderRepository.findReconcileCandidates(
                eq("MOMO"), eq(PaymentStatus.PENDING), eq(PaymentStatus.EXPIRED),
                any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());

        scheduler.reconcilePendingPayments();

        verifyNoInteractions(paymentService);
    }

    private static PaymentOrder order(String orderId) {
        PaymentOrder order = new PaymentOrder();
        order.setOrderId(orderId);
        order.setProvider("MOMO");
        return order;
    }
}
