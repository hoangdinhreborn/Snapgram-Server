package com.example.recommender.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("JobInstanceAlreadyCompleteException được map về HTTP 409 Conflict")
    void testHandleJobInstanceAlreadyCompleteException() {
        JobInstanceAlreadyCompleteException ex = new JobInstanceAlreadyCompleteException("Instance already completed");
        ResponseEntity<ErrorResponse> response = handler.handleJobConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("JOB_CONFLICT", response.getBody().getCode());
        assertEquals("Instance already completed", response.getBody().getMessage());
    }

    @Test
    @DisplayName("JobExecutionAlreadyRunningException được map về HTTP 409 Conflict")
    void testHandleJobExecutionAlreadyRunningException() {
        JobExecutionAlreadyRunningException ex = new JobExecutionAlreadyRunningException("Execution already running");
        ResponseEntity<ErrorResponse> response = handler.handleJobConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("JOB_CONFLICT", response.getBody().getCode());
    }

    @Test
    @DisplayName("JobRestartException được map về HTTP 409 Conflict")
    void testHandleJobRestartException() {
        JobRestartException ex = new JobRestartException("Job cannot be restarted");
        ResponseEntity<ErrorResponse> response = handler.handleJobConflict(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("JOB_CONFLICT", response.getBody().getCode());
    }

    @Test
    @DisplayName("NoSuchJobExecutionException được map về HTTP 404 Not Found")
    void testHandleNoSuchJobExecutionException() {
        NoSuchJobExecutionException ex = new NoSuchJobExecutionException("No such execution: 999");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("NOT_FOUND", response.getBody().getCode());
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException (id='null' hoặc không phải số) được map về HTTP 400 Bad Request")
    void testHandleMethodArgumentTypeMismatchException() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "null",
                Long.class,
                "id",
                null,
                new IllegalArgumentException("For input string: \"null\"")
        );

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BAD_REQUEST", response.getBody().getCode());
        assertEquals("Tham số 'id' không hợp lệ: giá trị 'null' không đúng định dạng số nguyên yêu cầu.",
                response.getBody().getMessage());
    }
}
