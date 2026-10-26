package com.example.payment.service;

import com.example.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentStateMachine {

    /**
     * Chuyen trang thai hop le. EXPIRED/FAILED/CANCELLED -> SUCCESS duoc phep:
     * provider co the thu tien truoc/sau khi scheduler danh dau het han
     * (IPN den tre, khach tra tien o phut cuoi). Da co chu ky + amount check
     * o tang tren nen day la su that "da thu tien" -> phai cap quyen.
     * REFUNDED la trang thai cuoi, khong quay lai SUCCESS.
     */
    public boolean canTransition(PaymentStatus from, PaymentStatus to) {
        return switch (from) {
            case PENDING -> to == PaymentStatus.SUCCESS
                    || to == PaymentStatus.FAILED
                    || to == PaymentStatus.EXPIRED
                    || to == PaymentStatus.CANCELLED;
            case SUCCESS -> to == PaymentStatus.REFUNDED;
            case EXPIRED, FAILED, CANCELLED -> to == PaymentStatus.SUCCESS;
            case REFUNDED -> false;
        };
    }

    public void checkTransition(PaymentStatus from, PaymentStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal payment transition: " + from + " -> " + to);
        }
    }
}
