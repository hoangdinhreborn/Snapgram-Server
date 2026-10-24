package com.example.payment.port;

public interface PaymentCatalog {
    ProductPrice getProduct(String productCode);
}
