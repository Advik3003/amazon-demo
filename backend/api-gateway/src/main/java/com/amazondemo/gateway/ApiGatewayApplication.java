package com.amazondemo.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API Gateway Application
 * ========================
 * Single entry point for all client requests (browser, mobile app, Postman).
 *
 * RESPONSIBILITIES:
 * 1. Route requests to appropriate microservices
 * 2. JWT Authentication validation (before forwarding)
 * 3. Rate Limiting (prevent abuse)
 * 4. Circuit Breaker (handle service failures gracefully)
 * 5. CORS handling
 * 6. Request/Response logging with Correlation IDs
 * 7. Aggregated Swagger UI (all services' APIs in one place)
 *
 * FLOW:
 * Client -> API Gateway (port 8080)
 *   -> JWT validation
 *   -> Rate limit check
 *   -> Route to service (e.g., product-service:8083)
 *   -> Return response
 *
 * NOTE: API Gateway uses Spring WebFlux (reactive) for high performance.
 * This means it's NON-BLOCKING and can handle many concurrent requests.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
