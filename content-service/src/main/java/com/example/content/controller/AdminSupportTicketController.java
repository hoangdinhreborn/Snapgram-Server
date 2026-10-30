package com.example.content.controller;

import com.example.content.dto.ticket.ReplySupportTicketRequest;
import com.example.content.dto.ticket.SupportTicketResponse;
import com.example.content.entity.SupportTicketCategory;
import com.example.content.entity.SupportTicketPriority;
import com.example.content.entity.SupportTicketStatus;
import com.example.content.service.SupportTicketService;
import com.example.content.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService ticketService;

    @GetMapping
    public ResponseEntity<Page<SupportTicketResponse>> getTickets(
            @RequestParam(required = false) SupportTicketStatus status,
            @RequestParam(required = false) SupportTicketCategory category,
            @RequestParam(required = false) SupportTicketPriority priority,
            Pageable pageable
    ) {
        UserContext.requirePermission("SUPPORT:TICKET_READ");
        return ResponseEntity.ok(ticketService.getAdminTickets(status, category, priority, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getTicketDetail(@PathVariable UUID id) {
        UserContext.requirePermission("SUPPORT:TICKET_READ");
        return ResponseEntity.ok(ticketService.getAdminTicketDetail(id));
    }

    @PatchMapping("/{id}/reply")
    public ResponseEntity<SupportTicketResponse> replyTicket(
            @RequestHeader(value = "X-User-Id", required = false) UUID responderId,
            @PathVariable UUID id,
            @Valid @RequestBody ReplySupportTicketRequest request
    ) {
        UserContext.requirePermission("SUPPORT:TICKET_MANAGE");
        return ResponseEntity.ok(ticketService.replyTicket(responderId, id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SupportTicketResponse> updateStatus(
            @RequestHeader(value = "X-User-Id", required = false) UUID responderId,
            @PathVariable UUID id,
            @RequestParam SupportTicketStatus status
    ) {
        UserContext.requirePermission("SUPPORT:TICKET_MANAGE");
        return ResponseEntity.ok(ticketService.updateTicketStatus(responderId, id, status));
    }
}
