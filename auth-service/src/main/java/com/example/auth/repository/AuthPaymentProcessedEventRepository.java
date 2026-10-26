package com.example.auth.repository;

import com.example.auth.entity.AuthPaymentProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthPaymentProcessedEventRepository extends JpaRepository<AuthPaymentProcessedEvent, String> {
}
