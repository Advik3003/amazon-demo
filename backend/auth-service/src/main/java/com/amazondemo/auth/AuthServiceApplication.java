package com.amazondemo.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth Service Application
 * =========================
 * Handles all authentication and authorization:
 * - User registration
 * - Login (returns JWT access + refresh token)
 * - Token refresh
 * - Logout (blacklists refresh token)
 * - Token validation endpoint for other services
 *
 * SECURITY ARCHITECTURE:
 * - Access Token (JWT): Short-lived (15 min), validated at API Gateway
 * - Refresh Token: Long-lived (7 days), stored in DB + Redis, used to get new access token
 * - On logout: refresh token is blacklisted in Redis
 * - Password: BCrypt encrypted (never stored in plain text)
 *
 * TOKEN BLACKLISTING:
 * - Why? JWT is stateless - once issued, it's valid until expiry
 * - If user logs out, we need to invalidate the token
 * - Solution: Store invalidated tokens in Redis until they expire
 * - Redis TTL matches token expiry so it auto-cleans up
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling  // For scheduled token cleanup tasks
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
