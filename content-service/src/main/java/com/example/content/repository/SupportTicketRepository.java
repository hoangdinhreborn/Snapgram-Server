package com.example.content.repository;

import com.example.content.entity.SupportTicket;
import com.example.content.entity.SupportTicketCategory;
import com.example.content.entity.SupportTicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

    Page<SupportTicket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportTicketStatus status, Pageable pageable);

    Page<SupportTicket> findByCategoryOrderByCreatedAtDesc(SupportTicketCategory category, Pageable pageable);
}
