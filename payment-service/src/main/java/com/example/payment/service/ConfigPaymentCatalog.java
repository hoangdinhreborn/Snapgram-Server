package com.example.payment.service;

import com.example.payment.port.PaymentCatalog;
import com.example.payment.port.ProductPrice;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
public class ConfigPaymentCatalog implements PaymentCatalog {

    private static final Map<String, ProductPrice> PRODUCTS = Map.of(
            "BLUE_TICK", new ProductPrice("BLUE_TICK", 199_000L, "VND", Duration.ofDays(30))
    );

    @Override
    public ProductPrice getProduct(String productCode) {
        ProductPrice price = PRODUCTS.get(productCode);
        if (price == null) {
            throw new IllegalArgumentException("Unknown product: " + productCode);
        }
        return price;
    }
}
