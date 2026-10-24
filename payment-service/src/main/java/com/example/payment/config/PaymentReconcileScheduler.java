package com.example.payment.config;

import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.NotFoundException;
import com.example.payment.repository.PaymentOrderRepository;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tu dong doi chieu don PENDING voi provider khi IPN khong den.
 * Chi chay cho provider cau hinh (mac dinh MOMO), batch nho de tranh spam API.
 */
@Component
@ConditionalOnProperty(prefix = "payment.reconcile", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class PaymentReconcileScheduler {

    private final PaymentService paymentService;
    private final PaymentOrderRepository orderRepository;
    private final PaymentReconcileProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelayString = "${payment.reconcile.interval-ms:60000}",
            initialDelayString = "${payment.reconcile.initial-delay-ms:30000}")
    public void reconcilePendingPayments() {
        // fixedDelay da tranh overlap; guard nay chan them khi instance bi goi dong thoi.
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            String provider = properties.getProvider() == null
                    ? "MOMO"
                    : properties.getProvider().trim().toUpperCase(Locale.ROOT);
            Instant now = Instant.now();
            Instant cutoff = now.minusSeconds(Math.max(0, properties.getMinAgeSeconds()));
            Instant expiredCutoff = now.minusSeconds(Math.max(0, properties.getExpiredGraceSeconds()));
            Pageable page = PageRequest.of(0, Math.max(1, properties.getBatchSize()));

            List<PaymentOrder> orders = orderRepository.findReconcileCandidates(
                    provider, PaymentStatus.PENDING, PaymentStatus.EXPIRED,
                    cutoff, expiredCutoff, page);
            int reconciled = 0;
            for (PaymentOrder order : orders) {
                try {
                    paymentService.reconcile(order.getOrderId());
                    reconciled++;
                    log.info("Auto-reconciled {} order {} -> SUCCESS", provider, order.getOrderId());
                } catch (NotFoundException | IllegalStateException e) {
                    // Provider chua bao thanh cong / chua tim thay: de lan sau thu lai.
                    log.debug("Auto-reconcile skipped {} order {}: {}",
                            provider, order.getOrderId(), e.getMessage());
                } catch (Exception e) {
                    log.warn("Auto-reconcile failed for {} order {}", provider, order.getOrderId(), e);
                }
            }
            if (reconciled > 0) {
                log.info("Auto-reconcile {}: {} order(s) marked SUCCESS", provider, reconciled);
            }
        } catch (Exception e) {
            log.error("Auto-reconcile scheduler failed", e);
        } finally {
            running.set(false);
        }
    }
}
