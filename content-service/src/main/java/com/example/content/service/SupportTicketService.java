package com.example.content.service;

import com.example.content.client.AuthServiceClient;
import com.example.content.dto.ticket.CreateSupportTicketRequest;
import com.example.content.dto.ticket.ReplySupportTicketRequest;
import com.example.content.dto.ticket.SupportTicketResponse;
import com.example.content.entity.SupportTicket;
import com.example.content.entity.SupportTicketCategory;
import com.example.content.entity.SupportTicketPriority;
import com.example.content.entity.SupportTicketStatus;
import com.example.content.exception.ContentNotFoundException;
import com.example.content.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final AuthServiceClient authServiceClient;

    /**
     * User: Tạo ticket hỗ trợ mới
     */
    @Transactional
    public SupportTicketResponse createTicket(UUID userId, CreateSupportTicketRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null");
        }

        SupportTicket ticket = SupportTicket.builder()
                .userId(userId)
                .category(request.getCategory())
                .subject(request.getSubject().trim())
                .description(request.getDescription().trim())
                .attachments(request.getAttachments() != null ? request.getAttachments() : java.util.Collections.emptyList())
                .priority(request.getPriority() != null ? request.getPriority() : SupportTicketPriority.NORMAL)
                .status(SupportTicketStatus.OPEN)
                .build();

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Created support ticket {} for user {}", saved.getId(), userId);
        return toResponse(saved);
    }

    /**
     * User: Lấy danh sách ticket của mình
     */
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> getMyTickets(UUID userId, Pageable pageable) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    /**
     * User: Lấy chi tiết ticket của mình
     */
    @Transactional(readOnly = true)
    public SupportTicketResponse getMyTicketDetail(UUID userId, UUID ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ContentNotFoundException("Ticket not found: " + ticketId));

        if (!ticket.getUserId().equals(userId)) {
            throw new ContentNotFoundException("Ticket not found or access denied");
        }

        return toResponse(ticket);
    }

    /**
     * Admin / Staff: Lấy danh sách toàn bộ ticket với bộ lọc
     */
    @Transactional(readOnly = true)
    public Page<SupportTicketResponse> getAdminTickets(
            SupportTicketStatus status,
            SupportTicketCategory category,
            SupportTicketPriority priority,
            Pageable pageable
    ) {
        Specification<SupportTicket> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            if (priority != null) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return ticketRepository.findAll(spec, sortedPageable)
                .map(this::toResponse);
    }

    /**
     * Admin / Staff: Xem chi tiết ticket
     */
    @Transactional(readOnly = true)
    public SupportTicketResponse getAdminTicketDetail(UUID ticketId) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ContentNotFoundException("Ticket not found: " + ticketId));
        return toResponse(ticket);
    }

    /**
     * Admin / Staff: Phản hồi ticket & cập nhật trạng thái
     */
    @Transactional
    public SupportTicketResponse replyTicket(UUID responderId, UUID ticketId, ReplySupportTicketRequest request) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ContentNotFoundException("Ticket not found: " + ticketId));

        ticket.setAdminReply(request.getReply().trim());
        ticket.setResponderId(responderId);
        
        SupportTicketStatus newStatus = request.getStatus() != null ? request.getStatus() : SupportTicketStatus.RESOLVED;
        ticket.setStatus(newStatus);
        if (newStatus == SupportTicketStatus.RESOLVED || newStatus == SupportTicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());
        }

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Ticket {} replied by responder {} with status {}", ticketId, responderId, newStatus);
        return toResponse(saved);
    }

    /**
     * Admin / Staff: Cập nhật nhanh trạng thái ticket
     */
    @Transactional
    public SupportTicketResponse updateTicketStatus(UUID responderId, UUID ticketId, SupportTicketStatus status) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ContentNotFoundException("Ticket not found: " + ticketId));

        ticket.setStatus(status);
        ticket.setResponderId(responderId);
        if (status == SupportTicketStatus.RESOLVED || status == SupportTicketStatus.CLOSED) {
            ticket.setResolvedAt(Instant.now());
        }

        SupportTicket saved = ticketRepository.save(ticket);
        log.info("Ticket {} status updated to {} by responder {}", ticketId, status, responderId);
        return toResponse(saved);
    }

    private SupportTicketResponse toResponse(SupportTicket t) {
        var builder = SupportTicketResponse.builder()
                .id(t.getId().toString())
                .userId(t.getUserId().toString())
                .category(t.getCategory())
                .subject(t.getSubject())
                .description(t.getDescription())
                .attachments(t.getAttachments())
                .status(t.getStatus())
                .priority(t.getPriority())
                .adminReply(t.getAdminReply())
                .responderId(t.getResponderId() != null ? t.getResponderId().toString() : null)
                .resolvedAt(t.getResolvedAt())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt());

        try {
            var author = authServiceClient.getUserSummary(t.getUserId());
            if (author != null) {
                builder.userUsername(author.getUsername())
                       .userDisplayName(author.getDisplayName())
                       .userAvatarUrl(author.getAvatarUrl());
            }
        } catch (Exception ignored) {}

        return builder.build();
    }
}
