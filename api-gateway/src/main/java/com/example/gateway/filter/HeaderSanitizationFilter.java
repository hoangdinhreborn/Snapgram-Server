package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * GlobalFilter: Strip/Sanitize untrusted internal headers coming from external clients.
 * Chạy ĐẦU TIÊN (Order = HIGHEST_PRECEDENCE) để chống triệt để Header Injection / Role Spoofing.
 */
@Component
@Slf4j
public class HeaderSanitizationFilter implements GlobalFilter, Ordered {

    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    // Danh sách các headers nội bộ tuyệt đối không cho phép client bên ngoài tự gửi lên
    private static final Set<String> UNTRUSTED_INTERNAL_HEADERS = Set.of(
            "X-User-Id",
            "X-Username",
            "X-User-Roles",
            "X-User-Permissions",
            "x-user-id",
            "x-username",
            "x-user-roles",
            "x-user-permissions"
    );

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    for (String headerName : UNTRUSTED_INTERNAL_HEADERS) {
                        List<String> removed = httpHeaders.remove(headerName);
                        if (removed != null && !removed.isEmpty()) {
                            log.warn("Blocked potentially malicious incoming internal header '{}' from {}",
                                    headerName, exchange.getRequest().getRemoteAddress());
                        }
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
