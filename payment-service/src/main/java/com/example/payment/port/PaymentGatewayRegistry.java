package com.example.payment.port;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chon gateway theo provider da luu tren don hang — them provider moi
 * (MoMo, ZaloPay...) chi can them adapter, khong sua PaymentService.
 */
@Component
public class PaymentGatewayRegistry {

    private final Map<String, PaymentGateway> byProvider;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.byProvider = gateways.stream()
                .collect(Collectors.toMap(
                        gw -> gw.provider().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    public PaymentGateway get(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Payment provider is required");
        }
        PaymentGateway gateway = byProvider.get(provider.trim().toUpperCase(Locale.ROOT));
        if (gateway == null) {
            throw new IllegalArgumentException("Unsupported payment provider: " + provider);
        }
        return gateway;
    }
}
