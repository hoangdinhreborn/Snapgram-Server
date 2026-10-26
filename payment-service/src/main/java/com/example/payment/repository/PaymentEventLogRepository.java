package com.example.payment.repository;

import com.example.payment.entity.PaymentEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface PaymentEventLogRepository extends JpaRepository<PaymentEventLog, UUID> {

    /**
     * Chen idempotent. Tra ve 0 neu event_id da ton tai.
     * Dung native ON CONFLICT thay vi saveAndFlush + catch: constraint violation
     * lam Hibernate session rollback-only, bat exception khong cuu duoc transaction.
     */
    @Modifying
    @Query(value = """
            INSERT INTO payment_event_log (id, event_id, order_id, provider, response_code, transaction_no, created_at)
            VALUES (:id, :eventId, :orderId, :provider, :responseCode, :transactionNo, :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") String eventId,
                       @Param("orderId") String orderId,
                       @Param("provider") String provider,
                       @Param("responseCode") String responseCode,
                       @Param("transactionNo") String transactionNo,
                       @Param("createdAt") Instant createdAt);
}
