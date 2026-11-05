package com.example.recommender.batch.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Điều khiển việc mô phỏng sự cố trong Spring Batch phục vụ kịch bản demo RESTART.
 * Đảm bảo lỗi cố ý chỉ được kích hoạt đúng MỘT lần duy nhất khi gọi API /run với failAtItem.
 * Khi gọi API /restart, switch không được arm lại -> job tiếp tục chạy thành công từ vị trí dừng.
 */
@Component
public class DemoFailureSwitch {

    private final AtomicBoolean armed = new AtomicBoolean(false);

    public void arm() {
        armed.set(true);
    }

    public boolean tryTrigger() {
        return armed.compareAndSet(true, false);
    }

    public void disarm() {
        armed.set(false);
    }

    public boolean isArmed() {
        return armed.get();
    }
}
