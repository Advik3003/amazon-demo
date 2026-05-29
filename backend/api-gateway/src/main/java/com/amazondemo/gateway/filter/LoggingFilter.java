package com.amazondemo.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Logging Filter
 * Logs all incoming requests and outgoing responses with timing information.
 * This is essential for monitoring API performance and debugging.
 */
@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");

        log.info("GATEWAY REQUEST  --> {} {} [correlationId={}]", method, path, correlationId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;

            log.info("GATEWAY RESPONSE <-- {} {} | Status: {} | Time: {}ms [correlationId={}]",
                    method, path, statusCode, duration, correlationId);
        }));
    }

    @Override
    public int getOrder() {
        return -2; // Run before JWT filter for logging
    }
}
