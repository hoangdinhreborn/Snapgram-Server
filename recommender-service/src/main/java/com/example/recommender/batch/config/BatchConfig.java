package com.example.recommender.batch.config;

import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.support.JobRegistrySmartInitializingSingleton;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class BatchConfig {

    /**
     * Tự động đăng ký tất cả các Job bean vào JobRegistry để JobOperator có thể tìm và restart.
     * Đây là API chuẩn của Spring Batch 6 thay thế cho JobRegistryBeanPostProcessor cũ.
     */
    @Bean
    public JobRegistrySmartInitializingSingleton jobRegistrySmartInitializingSingleton(JobRegistry jobRegistry) {
        return new JobRegistrySmartInitializingSingleton(jobRegistry);
    }

    /**
     * Async JobLauncher phục vụ việc gọi Job từ REST Controller không bị block HTTP request,
     * trả về JobExecutionId ngay lập tức cho client.
     */
    @Bean(name = "asyncJobLauncher")
    public JobLauncher asyncJobLauncher(JobRepository jobRepository) throws Exception {
        TaskExecutorJobLauncher launcher = new TaskExecutorJobLauncher();
        launcher.setJobRepository(jobRepository);
        launcher.setTaskExecutor(new SimpleAsyncTaskExecutor("batch-async-"));
        launcher.afterPropertiesSet();
        return launcher;
    }
}
