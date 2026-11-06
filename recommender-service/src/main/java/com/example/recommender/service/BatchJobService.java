package com.example.recommender.service;

import com.example.recommender.dto.batch.JobExecutionResponse;
import com.example.recommender.dto.batch.JobHistoryResponse;

public interface BatchJobService {

    /**
     * Kích hoạt thủ công một Spring Batch Job theo tên (bất đồng bộ).
     */
    JobExecutionResponse runJob(String jobName) throws Exception;

    /**
     * Lấy thông tin chi tiết và số liệu các step của một JobExecution.
     */
    JobExecutionResponse getExecution(Long id);

    /**
     * Lấy danh sách lịch sử thực thi của toàn bộ các Batch Jobs, gom nhóm và sắp xếp theo ID giảm dần.
     */
    JobHistoryResponse getExecutionHistory(int limit);

    /**
     * Khởi động lại (Restart) một JobExecution bị FAILED từ điểm dừng an toàn.
     */
    JobExecutionResponse restartJob(Long id) throws Exception;

    /**
     * Dừng một JobExecution đang chạy (nếu có).
     */
    JobExecutionResponse stopJob(Long id) throws Exception;
}
