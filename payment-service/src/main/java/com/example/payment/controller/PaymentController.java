package com.example.payment.controller;

import com.example.payment.adapter.momo.MomoPaymentGateway;
import com.example.payment.adapter.vnpay.VnpayPaymentGateway;
import com.example.payment.dto.payment.CreatePaymentRequest;
import com.example.payment.dto.payment.CreatePaymentResponse;
import com.example.payment.dto.payment.PaymentOrderResponse;
import com.example.payment.service.PaymentService;
import com.example.payment.util.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** POST /api/payments/vnpay/create — amount lay tu server theo productCode. */
    @PostMapping("/vnpay/create")
    public ResponseEntity<CreatePaymentResponse> createVnpay(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        return create(VnpayPaymentGateway.PROVIDER, request, httpRequest);
    }

    /** POST /api/payments/momo/create — cung flow, doi gateway sang MoMo. */
    @PostMapping("/momo/create")
    public ResponseEntity<CreatePaymentResponse> createMomo(
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        return create(MomoPaymentGateway.PROVIDER, request, httpRequest);
    }

    private ResponseEntity<CreatePaymentResponse> create(
            String provider, CreatePaymentRequest request, HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        CreatePaymentResponse response = paymentService.createPayment(
                UserContext.getCurrentUserId(), request.productCode(), clientIp, provider);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** GET /api/payments/{orderId} — chi owner hoac admin. */
    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentOrderResponse> getOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(paymentService.getOrder(
                UserContext.getCurrentUserId(), UserContext.isAdmin(), orderId));
    }

    /** GET /api/payments/me?page=0&size=20 */
    @GetMapping("/me")
    public ResponseEntity<Page<PaymentOrderResponse>> listMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        return ResponseEntity.ok(paymentService.listMine(UserContext.getCurrentUserId(), pageable));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        // VNPay chi nhan IPv4 — loopback IPv6 (::1) doi ve 127.0.0.1
        if (ip == null || ip.isBlank() || ip.contains(":")) {
            return "127.0.0.1";
        }
        return ip;
    }
}
