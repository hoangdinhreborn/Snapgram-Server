package com.example.recommender.batch.scheduler;

import com.example.recommender.batch.job.trending.TrendingPostsJobConfig;
import com.example.recommender.batch.job.user.UserRecommendationJobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class BatchJobScheduler {

    private final JobExplorer jobExplorer;

    @Qualifier("asyncJobLauncher")
    private final JobLauncher asyncJobLauncher;

    @Qualifier(TrendingPostsJobConfig.JOB_NAME)
    private final Job trendingPostsJob;

    @Qualifier(UserRecommendationJobConfig.JOB_NAME)
    private final Job userRecommendationJob;

    @Value("${recommender.batch.scheduled-enabled:false}")
    private boolean scheduledEnabled;

    /**
     * Chạy định kỳ job tính toán bài viết trending.
     */
    @Scheduled(cron = "${recommender.batch.trending-cron:0 0 */2 * * *}")
    public void scheduleTrendingPostsJob() {
        if (!scheduledEnabled) {
            log.debug("Batch scheduling bị tắt (recommender.batch.scheduled-enabled=false). Bỏ qua trendingPostsJob.");
            return;
        }
        triggerJobIfIdle(trendingPostsJob);
    }

    /**
     * Chạy định kỳ job tính toán gợi ý follow người dùng.
     */
    @Scheduled(cron = "${recommender.batch.user-rec-cron:0 0 3 * * *}")
    public void scheduleUserRecommendationJob() {
        if (!scheduledEnabled) {
            log.debug("Batch scheduling bị tắt (recommender.batch.scheduled-enabled=false). Bỏ qua userRecommendationJob.");
            return;
        }
        triggerJobIfIdle(userRecommendationJob);
    }

    private void triggerJobIfIdle(Job job) {
        String jobName = job.getName();
        Set<JobExecution> runningExecutions = jobExplorer.findRunningJobExecutions(jobName);
        if (!runningExecutions.isEmpty()) {
            log.warn("Job '{}' hiện đang có {} execution đang chạy, bỏ qua lần trigger định kỳ này để tránh xung đột.",
                    jobName, runningExecutions.size());
            return;
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("triggerSource", "SCHEDULER")
                    .toJobParameters();

            log.info("⏰ [SCHEDULER TRIGGER] Bắt đầu chạy tự động job: {}", jobName);
            asyncJobLauncher.run(job, params);
        } catch (Exception e) {
            log.error("Lỗi khi scheduler kích hoạt job '{}': {}", jobName, e.getMessage(), e);
        }
    }
}
