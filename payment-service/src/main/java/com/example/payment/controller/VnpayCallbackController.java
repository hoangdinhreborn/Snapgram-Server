package com.example.payment.controller;

import com.example.payment.adapter.vnpay.VnpayPaymentGateway;
import com.example.payment.dto.PaymentCallbackResult;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class VnpayCallbackController {

    private final PaymentService paymentService;

    @Value("${payment.frontend-result-url:http://localhost:3000/payment/result}")
    private String frontendResultUrl;

    /**
     * Return URL: VNPay redirect user ve day, backend 302 tiep ve frontend.
     * Chi verify chu ky de hien thi, KHONG doi trang thai DB — cho IPN quyet dinh.
     * Frontend poll GET /api/payments/{orderId} lay trang thai that.
     */
    @GetMapping("/return")
    public ResponseEntity<Void> vnpayReturn(@RequestParam Map<String, String> params) {
        PaymentCallbackResult result = paymentService.previewReturn(VnpayPaymentGateway.PROVIDER, params);
        String location = frontendResultUrl
                + "?orderId=" + encode(result.orderId())
                + "&responseCode=" + encode(result.responseCode())
                + "&signatureValid=" + result.signatureValid();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    /**
     * IPN: VNPay goi server-to-server. Nguon duy nhat cap nhat trang thai
     * va phat payment.completed.
     */
    @GetMapping("/ipn")
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> params) {
        return ResponseEntity.ok(paymentService.handleIpn(VnpayPaymentGateway.PROVIDER, params));
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
