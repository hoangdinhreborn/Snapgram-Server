package com.example.payment.controller;

import com.example.payment.adapter.momo.MomoPaymentGateway;
import com.example.payment.dto.payment.PaymentCallbackResult;
import com.example.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/momo")
@RequiredArgsConstructor
public class MomoCallbackController {

    private final PaymentService paymentService;

    @Value("${payment.frontend-result-url:http://localhost:3000/payment/result}")
    private String frontendResultUrl;

    /**
     * IPN: MoMo POST JSON server-to-server. Tra resultCode/message (format cua MoMo,
     * khac RspCode cua VNPay) va la nguon duy nhat cap nhat trang thai don hang.
     */
    @PostMapping("/ipn")
    public ResponseEntity<Map<String, String>> momoIpn(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(paymentService.handleIpn(MomoPaymentGateway.PROVIDER, toStringParams(body)));
    }

    /**
     * Return URL: MoMo redirect user ve day (GET query string), backend 302 tiep ve frontend.
     * Chi verify chu ky de hien thi, KHONG doi trang thai DB — cho IPN quyet dinh.
     * Frontend poll GET /api/payments/{orderId} lay trang thai that.
     */
    @GetMapping("/return")
    public ResponseEntity<Void> momoReturn(@RequestParam Map<String, String> params) {
        PaymentCallbackResult result = paymentService.previewReturn(MomoPaymentGateway.PROVIDER, params);
        String location = frontendResultUrl
                + "?orderId=" + encode(result.orderId())
                + "&responseCode=" + encode(result.responseCode())
                + "&signatureValid=" + result.signatureValid();
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(location)).build();
    }

    /** JSON body co so (amount, transId...) -> String de ky chu ky dung dung chu so goc. */
    private static Map<String, String> toStringParams(Map<String, Object> body) {
        Map<String, String> params = new HashMap<>();
        if (body != null) {
            body.forEach((key, value) -> params.put(key, value == null ? null : String.valueOf(value)));
        }
        return params;
    }

    private String encode(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
