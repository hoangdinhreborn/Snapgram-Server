package com.example.recommender.batch.job.userrecommendation;

import com.example.recommender.batch.config.BatchProperties;
import com.example.recommender.batch.listener.ChunkLoggingListener;
import com.example.recommender.batch.listener.ItemSkipListener;
import com.example.recommender.batch.listener.JobExecutionLoggingListener;
import com.example.recommender.entity.RecommenderUserRecommendation;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderFollowViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.data.RepositoryItemReader;
import org.springframework.batch.infrastructure.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.*;

@Configuration
@RequiredArgsConstructor
public class UserRecommendationJobConfig {

    public static final String JOB_NAME = "userRecommendationJob";
    public static final String STEP_NAME = "userRecommendationStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RecommenderFollowViewRepository followViewRepository;

    private final UserRecommendationProcessor userRecommendationProcessor;
    private final UserRecommendationWriter userRecommendationWriter;

    private final JobExecutionLoggingListener jobExecutionLoggingListener;
    private final ChunkLoggingListener chunkLoggingListener;
    private final ItemSkipListener itemSkipListener;

    private final BatchProperties batchProperties;

    @Bean
    @StepScope
    public RepositoryItemReader<UUID> userRecommendationReader() {
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("id.followerId", Sort.Direction.ASC);

        return new RepositoryItemReaderBuilder<UUID>()
                .name("userRecommendationReader")
                .repository(followViewRepository)
                .methodName("findDistinctFollowerIds")
                .arguments(Collections.emptyList())
                .pageSize(batchProperties.getChunkSize())
                .sorts(sorts)
                .saveState(true)
                .build();
    }

    @Bean
    public Step userRecommendationStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<UUID, List<RecommenderUserRecommendation>>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(userRecommendationReader())
                .processor(userRecommendationProcessor)
                .writer(userRecommendationWriter)
                .faultTolerant()
                .skip(MalformedPostException.class)
                .skipLimit(batchProperties.getSkipLimit())
                .listener(chunkLoggingListener)
                .listener(itemSkipListener)
                .build();
    }

    @Bean(name = JOB_NAME)
    public Job userRecommendationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobExecutionLoggingListener)
                .start(userRecommendationStep())
                .build();
    }
}
