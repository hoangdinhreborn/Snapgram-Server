package com.example.recommender.batch.listener;

import com.example.recommender.entity.RecommenderPostView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ItemSkipListener implements SkipListener<Object, Object> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("item_skip phase=read reason={}", t.getMessage());
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        String itemId = resolveItemId(item);
        log.warn("item_skip phase=process itemId={} exception={} reason={}",
                itemId, t.getClass().getSimpleName(), t.getMessage());
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        String itemId = resolveItemId(item);
        log.warn("item_skip phase=write itemId={} exception={} reason={}",
                itemId, t.getClass().getSimpleName(), t.getMessage());
    }

    private String resolveItemId(Object item) {
        if (item instanceof RecommenderPostView post) {
            return String.valueOf(post.getPostId());
        }
        return String.valueOf(item);
    }
}
