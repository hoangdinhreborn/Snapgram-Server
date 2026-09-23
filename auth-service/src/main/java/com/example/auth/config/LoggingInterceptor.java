package com.example.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.UUID;

@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String REQUEST_START_TIME = "requestStartTime";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        // Generate unique request ID
        String requestId = UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        request.setAttribute(REQUEST_START_TIME, System.currentTimeMillis());

        // Add request ID to response
        response.addHeader(REQUEST_ID_HEADER, requestId);

        // Log request details
        logRequest(request, requestId);

        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) throws Exception {
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        long startTime = (long) request.getAttribute(REQUEST_START_TIME);
        long duration = System.currentTimeMillis() - startTime;

        // Log response details
        logResponse(request, response, duration, requestId, ex);
    }

    private void logRequest(HttpServletRequest request, String requestId) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String remoteAddr = getClientIp(request);

        log.info("[{}] {} {} from {} | Query: {}",
                requestId,
                method,
                uri,
                remoteAddr,
                queryString != null ? queryString : "N/A"
        );

        // Log auth header if present (masked)
        String auth = request.getHeader("Authorization");
        if (auth != null) {
            String maskedAuth = auth.substring(0, Math.min(20, auth.length())) + "...";
            log.debug("[{}] Authorization: {}", requestId, maskedAuth);
        }

        // Log content type
        String contentType = request.getContentType();
        if (contentType != null) {
            log.debug("[{}] Content-Type: {}", requestId, contentType);
        }
    }

    private void logResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            long duration,
            String requestId,
            Exception exception
    ) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        int status = response.getStatus();

        if (exception != null) {
            log.error("[{}] {} {} completed with error in {}ms | Status: {} | Error: {}",
                    requestId,
                    method,
                    uri,
                    duration,
                    status,
                    exception.getMessage()
            );
        } else {
            HttpStatus httpStatus = HttpStatus.resolve(status);
            if (httpStatus != null && httpStatus.isError()) {
                log.warn("[{}] {} {} completed with error in {}ms | Status: {} ({})",
                        requestId,
                        method,
                        uri,
                        duration,
                        status,
                        httpStatus.getReasonPhrase()
                );
            } else {
                log.info("[{}] {} {} completed in {}ms | Status: {}",
                        requestId,
                        method,
                        uri,
                        duration,
                        status
                );
            }
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        return clientIp;
    }
}
