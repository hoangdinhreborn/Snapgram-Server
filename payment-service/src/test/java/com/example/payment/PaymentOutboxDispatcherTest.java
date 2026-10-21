package com.example.payment;

import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentOutbox;
import com.example.payment.port.PaymentEventPublisher;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.service.PaymentOutboxDispatcher;
import com.example.payment.service.PaymentOutboxStateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOutboxDispatcherTest {

    private final PaymentOutboxStateService stateService = mock(PaymentOutboxStateService.class);
    private final PaymentOrderRepository orderRepository = mock(PaymentOrderRepository.class);
    private final PaymentEventPublisher publisher = mock(PaymentEventPublisher.class);

    @Test
    void dispatchPending_publishSuccess_marksSent() {
        PaymentOutbox outbox = outbox();
        PaymentOrder order = order();
        when(stateService.claimBatch(anyInt(), any(Duration.class))).thenReturn(List.of(outbox));
        when(orderRepository.findByOrderId("order1")).thenReturn(Optional.of(order));
        when(publisher.publishPaymentCompleted(order)).thenReturn(CompletableFuture.completedFuture(null));

        dispatcher().dispatchPending();

        verify(stateService).markSent(outbox.getId());
        verify(stateService, never()).markFailed(any(), anyString(), anyInt());
    }

    @Test
    void dispatchPending_kafkaFailure_marksFailedForRetry() {
        PaymentOutbox outbox = outbox();
        PaymentOrder order = order();
        when(stateService.claimBatch(anyInt(), any(Duration.class))).thenReturn(List.of(outbox));
        when(orderRepository.findByOrderId("order1")).thenReturn(Optional.of(order));
        when(publisher.publishPaymentCompleted(order)).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("kafka down")));

        dispatcher().dispatchPending();

        verify(stateService).markFailed(eq(outbox.getId()), anyString(), eq(3));
        verify(stateService, never()).markSent(any());
    }

    private PaymentOutboxDispatcher dispatcher() {
        PaymentOutboxDispatcher dispatcher =
                new PaymentOutboxDispatcher(stateService, orderRepository, publisher);
        ReflectionTestUtils.setField(dispatcher, "batchSize", 50);
        ReflectionTestUtils.setField(dispatcher, "leaseSeconds", 60L);
        ReflectionTestUtils.setField(dispatcher, "maxAttempts", 3);
        ReflectionTestUtils.setField(dispatcher, "sendTimeoutSeconds", 1L);
        return dispatcher;
    }

    private static PaymentOutbox outbox() {
        PaymentOutbox outbox = new PaymentOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setOrderId("order1");
        outbox.setEventId("momo:order1:txn1");
        outbox.setAttempts(1);
        return outbox;
    }

    private static PaymentOrder order() {
        PaymentOrder order = new PaymentOrder();
        order.setOrderId("order1");
        order.setProvider("MOMO");
        order.setAmount(199_000L);
        return order;
    }
}
