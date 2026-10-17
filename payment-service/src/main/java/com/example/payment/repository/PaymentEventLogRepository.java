package com.example.payment.repository;

import com.example.payment.entity.PaymentEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentEventLogRepository extends JpaRepository<PaymentEventLog, UUID> {

    boolean existsByEventId(String eventId);
}
