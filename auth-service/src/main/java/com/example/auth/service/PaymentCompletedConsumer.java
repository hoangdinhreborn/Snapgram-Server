package com.example.auth.service;

import com.example.auth.config.PaymentGrantProperties;
import com.example.auth.dto.PaymentCompletedEvent;
import com.example.auth.entity.AuthPaymentProcessedEvent;
import com.example.auth.entity.AuthUser;
import com.example.auth.repository.AuthPaymentProcessedEventRepository;
import com.example.auth.repository.AuthUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Nhan payment.completed de cap Blue Tick — bang auth_payment_processed_events
 * va cong thuc verified_until da duoc chuan bi san trong migration V6.
 * Idempotency: event_id la PK, IPN + reconcile deu ghi cung 1 eventId nen retry an toan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private final AuthUserRepository userRepository;
    private final AuthPaymentProcessedEventRepository eventRepository;
    private final PaymentGrantProperties grantProperties;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @KafkaListener(topics = "${payment.kafka.topic-completed:payment.completed}")
    @Transactional
    public void onPaymentCompleted(String payload) {
        PaymentCompletedEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
        } catch (Exception ex) {
            // Poison pill: bo qua thay vi retry moi lan de khong chan partition.
            log.error("payment.completed khong parse duoc, bo qua: {}", abbreviate(payload), ex);
            return;
        }
        if (event.eventId() == null || event.userId() == null || event.productCode() == null) {
            log.warn("payment.completed thieu truong bat buoc, bo qua: {}", event);
            return;
        }
        if (eventRepository.existsById(event.eventId())) {
            log.info("payment.completed da xu ly truoc, skip eventId={}", event.eventId());
            return;
        }
        Duration grant = grantProperties.getGrants().get(event.productCode());
        if (grant == null) {
            log.error("Chua cau hinh thoi han cho productCode={} (payment.grants.*), khong cap quyen eventId={}",
                    event.productCode(), event.eventId());
            return;
        }
        AuthUser user = userRepository.findById(event.userId()).orElse(null);
        if (user == null) {
            log.error("Khong tim thay user {} cho eventId={}, khong cap quyen", event.userId(), event.eventId());
            return;
        }

        Instant now = Instant.now();
        // Cong don: neu con han thi keu tu han cu, het han thi keu tu bay gio.
        Instant base = (user.getVerifiedUntil() != null && user.getVerifiedUntil().isAfter(now))
                ? user.getVerifiedUntil()
                : now;
        Instant grantedUntil = base.plus(grant);

        user.setVerified(true);
        user.setVerifiedUntil(grantedUntil);
        userRepository.save(user);
        AuthPaymentProcessedEvent processed = new AuthPaymentProcessedEvent();
        processed.setEventId(event.eventId());
        processed.setUserId(event.userId());
        processed.setProductCode(event.productCode());
        processed.setGrantedUntil(grantedUntil);
        eventRepository.save(processed);
        log.info("Cap Blue Tick user={} product={} provider={} den {}",
                event.userId(), event.productCode(), event.provider(), grantedUntil);

        // Báo cho các service khác (content-service...) xoá cache hồ sơ user
        // để huy hiệu tích xanh hiện ra ngay, không phải chờ TTL.
        publishBlueTickChanged(event.userId(), grantedUntil);
    }

    private void publishBlueTickChanged(java.util.UUID userId, java.time.Instant verifiedUntil) {
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("eventType", "BLUE_TICK_CHANGED");
            payload.put("userId", userId.toString());
            payload.put("verified", true);
            payload.put("verifiedUntil", verifiedUntil != null ? verifiedUntil.toString() : null);
            payload.put("timestamp", java.time.Instant.now().toString());
            kafkaTemplate.send("user.events", userId.toString(), payload);
        } catch (Exception e) {
            log.warn("Khong publish duoc BLUE_TICK_CHANGED cho user {}: {}", userId, e.getMessage());
        }
    }

    private static String abbreviate(String payload) {
        if (payload == null) {
            return "<null>";
        }
        return payload.length() > 200 ? payload.substring(0, 200) + "..." : payload;
    }
}
