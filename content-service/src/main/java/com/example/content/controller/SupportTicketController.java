package com.example.content.controller;

import com.example.content.dto.ticket.CreateSupportTicketRequest;
import com.example.content.dto.ticket.SupportTicketResponse;
import com.example.content.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService ticketService;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> createTicket(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateSupportTicketRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.createTicket(userId, request));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<SupportTicketResponse>> getMyTickets(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ticketService.getMyTickets(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getMyTicketDetail(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ticketService.getMyTicketDetail(userId, id));
    }
}
