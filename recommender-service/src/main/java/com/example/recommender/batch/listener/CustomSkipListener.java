package com.example.recommender.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CustomSkipListener implements SkipListener<Object, Object> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("⚠️ [SKIP-ON-READ] Bỏ qua bản ghi khi đọc do lỗi: {}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        log.warn("⚠️ [SKIP-ON-PROCESS] Bỏ qua item [{}] do lỗi nghiệp vụ ({}): {}",
                item, t.getClass().getSimpleName(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        log.warn("⚠️ [SKIP-ON-WRITE] Bỏ qua item [{}] khi ghi DB do lỗi: {}",
                item, t.getMessage());
    }
}
