package com.example.payment.controller;

import com.example.payment.dto.PaymentOrderResponse;
import com.example.payment.service.PaymentService;
import com.example.payment.util.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "amount", "expiredAt", "status");

    private final PaymentService paymentService;

    /** GET /api/admin/payments?status=&userId=&productCode=&sort=createdAt,desc&page=0&size=20 */
    @GetMapping
    public ResponseEntity<Page<PaymentOrderResponse>> listAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String productCode,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserContext.requireAdmin();
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size, parseSort(sort));
        return ResponseEntity.ok(paymentService.listAll(status, userId, productCode, pageable));
    }

    /**
     * POST /api/admin/payments/{orderId}/reconcile — doi chieu thu cong voi VNPay
     * qua QueryDR khi IPN that lac. Chi admin.
     */
    @PostMapping("/{orderId}/reconcile")
    public ResponseEntity<PaymentOrderResponse> reconcile(@PathVariable String orderId) {
        UserContext.requireAdmin();
        return ResponseEntity.ok(paymentService.reconcile(orderId));
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String[] parts = sortParam.split(",");
        String field = ALLOWED_SORT_FIELDS.contains(parts[0].trim()) ? parts[0].trim() : "createdAt";
        Sort.Direction direction = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
