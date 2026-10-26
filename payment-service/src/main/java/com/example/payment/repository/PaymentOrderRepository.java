package com.example.payment.repository;

import com.example.payment.entity.PaymentOrder;
import com.example.payment.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID>,
        JpaSpecificationExecutor<PaymentOrder> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.orderId = :orderId")
    Optional<PaymentOrder> findByOrderIdForUpdate(String orderId);

    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Ung vien cho scheduler doi chieu provider: PENDING du lau, hoac EXPIRED
     * trong khoang grace de bat kip giao dich den tre. Uu tien don cu truoc.
     */
    @Query("""
            select p from PaymentOrder p
            where p.provider = :provider
              and ((p.status = :pending and p.createdAt < :createdBefore)
                or (p.status = :expired and p.expiredAt > :expiredAfter))
            order by p.createdAt asc
            """)
    List<PaymentOrder> findReconcileCandidates(
            @Param("provider") String provider,
            @Param("pending") PaymentStatus pending,
            @Param("expired") PaymentStatus expired,
            @Param("createdBefore") Instant createdBefore,
            @Param("expiredAfter") Instant expiredAfter,
            Pageable pageable);

    /**
     * Chuyen PENDING qua han thanh EXPIRED bang 1 query atomic.
     * Dieu kien status='PENDING' giup khong race voi IPN: neu IPN da xu ly
     * (giua lock row) thi row khong khop dieu kien -> 0 row affected.
     */
    @Modifying
    @Query("update PaymentOrder p set p.status = :expired where p.status = :pending and p.expiredAt < :now")
    int expireOverdue(@Param("pending") PaymentStatus pending,
                      @Param("expired") PaymentStatus expired,
                      @Param("now") Instant now);
}
