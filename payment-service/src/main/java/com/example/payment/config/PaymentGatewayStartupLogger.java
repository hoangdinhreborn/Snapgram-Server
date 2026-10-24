package com.example.payment.config;

import com.example.payment.adapter.momo.MomoProperties;
import com.example.payment.adapter.vnpay.VnpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * In trang thai cau hinh gateway ngay khi boot — thieu key se thay log canh bao
 * tai day thay vi loi 400 lap lap khi goi /create.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentGatewayStartupLogger implements ApplicationRunner {

    private final VnpayProperties vnpay;
    private final MomoProperties momo;

    @Override
    public void run(ApplicationArguments args) {
        if (vnpay.isConfigured()) {
            log.info("VNPay da cau hinh: tmnCode={}", vnpay.getTmnCode());
        } else {
            log.warn("VNPay CHUA cau hinh (thieu VNPAY_TMN_CODE/VNPAY_HASH_SECRET)"
                    + " — POST /api/payments/vnpay/create se tra 400");
        }

        if (momo.isConfigured()) {
            log.info("MoMo da cau hinh: partnerCode={}, baseUrl={}", momo.getPartnerCode(), momo.getBaseUrl());
            if (localUrl(momo.getIpnUrl())) {
                log.warn("MOMO_IPN_URL={} khong goi duoc tu MoMo (localhost/khong truy cap du)"
                        + " — can URL public (tunnel/ngrok)", momo.getIpnUrl());
            }
        } else {
            log.warn("MoMo CHUA cau hinh (thieu MOMO_PARTNER_CODE/MOMO_ACCESS_KEY/MOMO_SECRET_KEY)"
                    + " — POST /api/payments/momo/create se tra 400");
        }
    }

    private static boolean localUrl(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("localhost") || lower.contains("127.0.0.1");
    }
}
