package com.example.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;
    
    @Autowired
    private LoggingInterceptor loggingInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Logging interceptor (runs first for all requests)
        registry.addInterceptor(loggingInterceptor)
                .addPathPatterns("/api/**", "/actuator/**");
        
        // Rate limiting interceptor
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/auth/**")
                .excludePathPatterns("/api/auth/verify"); // Exclude inter-service calls
    }
}
