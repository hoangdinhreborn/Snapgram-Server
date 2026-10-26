package com.example.payment.repository;

import com.example.payment.entity.PaymentOutbox;
import com.example.payment.entity.PaymentOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, UUID> {

    /**
     * Chen outbox idempotent: 1 don chi co toi da 1 event payment.completed.
     * ON CONFLICT giup khong lam hong transaction khi IPN/reconcile chay trung.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment_outbox (id, order_id, event_id, status, attempts, next_attempt_at, created_at)
            VALUES (:id, :orderId, :eventId, 'PENDING', 0, :now, :now)
            ON CONFLICT (order_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("orderId") String orderId,
                       @Param("eventId") String eventId,
                       @Param("now") Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT outbox
            FROM PaymentOutbox outbox
            WHERE (outbox.status = :pending AND outbox.nextAttemptAt <= :now)
               OR (outbox.status = :processing AND outbox.leaseUntil <= :now)
            ORDER BY outbox.createdAt ASC
            """)
    List<PaymentOutbox> findClaimable(@Param("pending") PaymentOutboxStatus pending,
                                      @Param("processing") PaymentOutboxStatus processing,
                                      @Param("now") Instant now,
                                      Pageable pageable);
}
