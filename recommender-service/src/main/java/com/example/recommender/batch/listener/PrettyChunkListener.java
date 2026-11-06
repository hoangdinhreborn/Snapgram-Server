package com.example.recommender.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PrettyChunkListener implements ChunkListener {

    @Override
    public void afterChunk(ChunkContext context) {
        StepContext stepContext = context.getStepContext();
        StepExecution stepExecution = stepContext.getStepExecution();

        long commitCount = stepExecution.getCommitCount();
        long readCount = stepExecution.getReadCount();
        long writeCount = stepExecution.getWriteCount();
        long skipCount = stepExecution.getSkipCount();

        log.info("[CHUNK COMMITTED] Step: [{}] | Chunk #{} | Read: {}, Written: {}, Skipped: {}",
                stepExecution.getStepName(), commitCount, readCount, writeCount, skipCount);
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        StepExecution stepExecution = context.getStepContext().getStepExecution();
        log.warn("⚠️ [CHUNK ROLLBACK] Step: [{}] encountered an error during chunk execution",
                stepExecution.getStepName());
    }
}
