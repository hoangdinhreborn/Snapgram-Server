package com.example.recommender.batch.job.trending;

import com.example.recommender.batch.config.BatchProperties;
import com.example.recommender.batch.listener.ChunkLoggingListener;
import com.example.recommender.batch.listener.ItemSkipListener;
import com.example.recommender.batch.listener.JobExecutionLoggingListener;
import com.example.recommender.entity.RecommenderPostView;
import com.example.recommender.entity.RecommenderTrendingPost;
import com.example.recommender.exception.MalformedPostException;
import com.example.recommender.repository.RecommenderPostViewRepository;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class TrendingPostsJobConfig {

    public static final String JOB_NAME = "trendingPostsJob";
    public static final String STEP_NAME = "trendingPostsStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final RecommenderPostViewRepository postViewRepository;

    private final TrendingPostProcessor trendingPostProcessor;
    private final TrendingPostWriter trendingPostWriter;
    private final TrendingPostRedisUpdateListener trendingPostRedisUpdateListener;

    private final JobExecutionLoggingListener jobExecutionLoggingListener;
    private final ChunkLoggingListener chunkLoggingListener;
    private final ItemSkipListener itemSkipListener;

    private final BatchProperties batchProperties;

    @Bean
    @StepScope
    public RepositoryItemReader<RecommenderPostView> trendingPostReader() {
        // Sử dụng LinkedHashMap để đảm bảo thứ tự sort ổn định: createdAt DESC rồi postId ASC (tie-breaker duy nhất)
        Map<String, Sort.Direction> sorts = new LinkedHashMap<>();
        sorts.put("createdAt", Sort.Direction.DESC);
        sorts.put("postId", Sort.Direction.ASC);

        return new RepositoryItemReaderBuilder<RecommenderPostView>()
                .name("trendingPostReader")
                .repository(postViewRepository)
                .methodName("findByVisibility")
                .arguments(List.of("PUBLIC"))
                .pageSize(batchProperties.getChunkSize())
                .sorts(sorts)
                .saveState(true)
                .build();
    }

    @Bean
    public Step trendingPostsStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<RecommenderPostView, RecommenderTrendingPost>chunk(batchProperties.getChunkSize(), transactionManager)
                .reader(trendingPostReader())
                .processor(trendingPostProcessor)
                .writer(trendingPostWriter)
                .faultTolerant()
                .skip(MalformedPostException.class)
                .skipLimit(batchProperties.getSkipLimit())
                .retry(org.springframework.dao.TransientDataAccessException.class)
                .retryLimit(3)
                .listener(chunkLoggingListener)
                .listener(itemSkipListener)
                .listener(trendingPostRedisUpdateListener)
                .build();
    }

    @Bean(name = JOB_NAME)
    public Job trendingPostsJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobExecutionLoggingListener)
                .start(trendingPostsStep())
                .build();
    }
}
