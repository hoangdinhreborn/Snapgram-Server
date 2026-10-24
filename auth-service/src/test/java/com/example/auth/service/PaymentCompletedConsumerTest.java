package com.example.auth.service;

import com.example.auth.config.PaymentGrantProperties;
import com.example.auth.entity.AuthPaymentProcessedEvent;
import com.example.auth.entity.AuthUser;
import com.example.auth.repository.AuthPaymentProcessedEventRepository;
import com.example.auth.repository.AuthUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentCompletedConsumerTest {

    private AuthUserRepository userRepository;
    private AuthPaymentProcessedEventRepository eventRepository;
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;
    private PaymentCompletedConsumer consumer;

    @BeforeEach
    void setUp() {
        userRepository = mock(AuthUserRepository.class);
        eventRepository = mock(AuthPaymentProcessedEventRepository.class);
        @SuppressWarnings("unchecked")
        org.springframework.kafka.core.KafkaTemplate<String, Object> kafka =
                mock(org.springframework.kafka.core.KafkaTemplate.class);
        kafkaTemplate = kafka;
        PaymentGrantProperties grants = new PaymentGrantProperties();
        grants.getGrants().put("BLUE_TICK", Duration.ofDays(30));
        consumer = new PaymentCompletedConsumer(userRepository, eventRepository, grants, kafkaTemplate);
    }

    private static String payload(String eventId, String productCode, UUID userId) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"orderId\":\"order123\",\"userId\":\"" + userId + "\",\"productCode\":\"" + productCode + "\","
                + "\"amount\":199000,\"currency\":\"VND\",\"provider\":\"MOMO\",\"transactionId\":\"4088878653\","
                + "\"occurredAt\":\"2026-10-17T10:15:30Z\"}";
    }

    @Test
    @DisplayName("Lan dau: verified=true + verified_until = now + 30d, ghi idempotency")
    void grantsBlueTickForNewUser() {
        UUID userId = UUID.randomUUID();
        AuthUser user = new AuthUser();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Instant before = Instant.now();
        consumer.onPaymentCompleted(payload("evt-1", "BLUE_TICK", userId));
        Instant after = Instant.now();

        ArgumentCaptor<AuthUser> savedUser = ArgumentCaptor.forClass(AuthUser.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().isVerified()).isTrue();
        assertThat(savedUser.getValue().getVerifiedUntil())
                .isBetween(before.plus(Duration.ofDays(30)), after.plus(Duration.ofDays(30)));

        ArgumentCaptor<AuthPaymentProcessedEvent> savedEvent = ArgumentCaptor.forClass(AuthPaymentProcessedEvent.class);
        verify(eventRepository).save(savedEvent.capture());
        assertThat(savedEvent.getValue().getEventId()).isEqualTo("evt-1");
        assertThat(savedEvent.getValue().getProductCode()).isEqualTo("BLUE_TICK");
    }

    @Test
    @DisplayName("Con han: keu tu han cu, khong reset ve now (cong don nhu migration V6)")
    void extendsExistingVerifiedUntil() {
        UUID userId = UUID.randomUUID();
        AuthUser user = new AuthUser();
        user.setId(userId);
        Instant existing = Instant.now().plus(Duration.ofDays(10));
        user.setVerifiedUntil(existing);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        consumer.onPaymentCompleted(payload("evt-2", "BLUE_TICK", userId));

        ArgumentCaptor<AuthUser> savedUser = ArgumentCaptor.forClass(AuthUser.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getVerifiedUntil())
                .isBetween(existing.plus(Duration.ofDays(30)).minusSeconds(5),
                        existing.plus(Duration.ofDays(30)).plusSeconds(5));
    }

    @Test
    @DisplayName("Idempotency: eventId da xu ly thi skip, khong cham vao user")
    void skipsDuplicateEventId() {
        when(eventRepository.existsById("evt-1")).thenReturn(true);

        consumer.onPaymentCompleted(payload("evt-1", "BLUE_TICK", UUID.randomUUID()));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Product khong co trong payment.grants.* thi log loi, khong cap quyen")
    void skipsUnknownProductCode() {
        consumer.onPaymentCompleted(payload("evt-3", "UNKNOWN_CODE", UUID.randomUUID()));

        verify(userRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("User khong ton tai thi khong ghi idempotency de retry sau duoc khi fix du lieu")
    void skipsMissingUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        consumer.onPaymentCompleted(payload("evt-4", "BLUE_TICK", userId));

        verify(userRepository).findById(userId);
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("JSON hong thi bo qua, khong quan listener")
    void skipsMalformedJson() {
        consumer.onPaymentCompleted("khong phai json {");

        verify(userRepository, never()).findById(any());
        verify(eventRepository, never()).save(any());
    }
}
