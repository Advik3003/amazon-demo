package com.amazondemo.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.List;

/**
 * JWT Authentication Filter
 * ==========================
 * Global filter that validates JWT tokens on every request BEFORE
 * forwarding to microservices.
 *
 * WHY VALIDATE AT GATEWAY LEVEL?
 * - Centralized security - no need to validate in every service
 * - If JWT is invalid, request is rejected immediately (no wasted service calls)
 * - Services trust the gateway and can focus on business logic
 *
 * HOW JWT VALIDATION WORKS:
 * 1. Client sends: Authorization: Bearer eyJhbGc...
 * 2. Gateway extracts the token
 * 3. Gateway verifies signature with the secret key
 * 4. If valid, extract user claims (userId, roles, email)
 * 5. Add claims as headers (X-User-Id, X-User-Roles) to forward to services
 * 6. Services trust these headers (they come from gateway, not client)
 *
 * PUBLIC PATHS: Some paths don't need authentication (login, register, etc.)
 */
@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${app.jwt.secret:amazondemo-super-secret-jwt-key-change-in-production-min-256-bits}")
    private String jwtSecret;

    private final ReactiveStringRedisTemplate redisTemplate;

    public JwtAuthenticationFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Paths that don't require authentication
    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/products",          // Public product browsing
            "/api/v1/products/",
            "/api/v1/categories",
            "/actuator",
            "/swagger-ui",
            "/api-docs",
            "/v3/api-docs",
            "/fallback"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Add Correlation ID to every request for distributed tracing
        String correlationId = request.getHeaders().getFirst("X-Correlation-ID");
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = java.util.UUID.randomUUID().toString();
        }
        final String finalCorrelationId = correlationId;

        // Check if this is a public path - skip auth
        if (isPublicPath(path)) {
            log.debug("Public path accessed: {} [correlationId={}]", path, finalCorrelationId);
            return chain.filter(exchange.mutate()
                    .request(r -> r.header("X-Correlation-ID", finalCorrelationId))
                    .build());
        }

        // Extract JWT from Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {} [correlationId={}]",
                    path, finalCorrelationId);
            return unauthorizedResponse(exchange);
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        try {
            // Validate and parse JWT
            Claims claims = parseJwtToken(token);

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String roles = claims.get("roles", String.class);

            final String finalUserId = userId;
            final String finalEmail = email;
            final String finalRoles = roles;

            // Check if token is blacklisted in Redis (logged out)
            String blacklistKey = "blacklist:" + token;
            return redisTemplate.hasKey(blacklistKey)
                    .flatMap(isBlacklisted -> {
                        if (Boolean.TRUE.equals(isBlacklisted)) {
                            log.warn("Blacklisted token used for path: {} [correlationId={}]", path, finalCorrelationId);
                            return unauthorizedResponse(exchange);
                        }

                        log.debug("JWT validated for user: {} path: {} [correlationId={}]",
                                finalUserId, path, finalCorrelationId);

                        // Forward enriched request with user context headers
                        ServerHttpRequest enrichedRequest = request.mutate()
                                .header("X-User-Id", finalUserId)
                                .header("X-User-Email", finalEmail != null ? finalEmail : "")
                                .header("X-User-Roles", finalRoles != null ? finalRoles : "")
                                .header("X-Correlation-ID", finalCorrelationId)
                                .build();

                        return chain.filter(exchange.mutate().request(enrichedRequest).build());
                    })
                    .onErrorResume(e -> {
                        // If Redis is down, fall back to allowing the request (fail open)
                        log.warn("Redis check failed, proceeding without blacklist check: {}", e.getMessage());
                        ServerHttpRequest enrichedRequest = request.mutate()
                                .header("X-User-Id", finalUserId)
                                .header("X-User-Email", finalEmail != null ? finalEmail : "")
                                .header("X-User-Roles", finalRoles != null ? finalRoles : "")
                                .header("X-Correlation-ID", finalCorrelationId)
                                .build();
                        return chain.filter(exchange.mutate().request(enrichedRequest).build());
                    });

        } catch (Exception e) {
            log.warn("JWT validation failed for path: {} - Error: {} [correlationId={}]",
                    path, e.getMessage(), finalCorrelationId);
            return unauthorizedResponse(exchange);
        }
    }

    private Claims parseJwtToken(String token) {
        javax.crypto.SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, "application/json");
        String body = "{\"status\":\"ERROR\",\"message\":\"Unauthorized: Invalid or missing token\"}";
        var buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters (higher priority)
    }
}
