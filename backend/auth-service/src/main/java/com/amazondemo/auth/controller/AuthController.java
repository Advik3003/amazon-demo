package com.amazondemo.auth.controller;

import com.amazondemo.auth.dto.AuthResponse;
import com.amazondemo.auth.dto.LoginRequest;
import com.amazondemo.auth.dto.RegisterRequest;
import com.amazondemo.auth.service.AuthService;
import com.amazondemo.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth Controller
 * ================
 * REST endpoints for authentication operations.
 *
 * @Tag - Swagger UI grouping
 * @Operation - Swagger UI documentation for each endpoint
 * @Valid - Triggers Bean Validation on request body
 *
 * All endpoints follow the pattern: /api/v1/auth/...
 * The "v1" in the URL enables API versioning - if we change the API,
 * we create /api/v2/auth/... without breaking existing clients.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User registration, login, token refresh and logout")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * Register a new user account
     */
    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account and returns JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received for email: {}", request.getEmail());
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authResponse, "Registration successful"));
    }

    /**
     * POST /api/v1/auth/login
     * Login with email and password
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user and returns JWT access and refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for email: {}", request.getEmail());
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Login successful"));
    }

    /**
     * POST /api/v1/auth/refresh
     * Get a new access token using a refresh token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Exchanges a valid refresh token for a new access token (implements token rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @RequestBody Map<String, String> request) {

        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("refreshToken is required", null));
        }

        AuthResponse authResponse = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.success(authResponse, "Token refreshed successfully"));
    }

    /**
     * POST /api/v1/auth/logout
     * Logout - revokes refresh token and blacklists access token
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes the refresh token and blacklists the access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String refreshToken = request.get("refreshToken");
        String accessToken = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
        }

        authService.logout(refreshToken, accessToken);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * GET /api/v1/auth/validate
     * Validate a JWT token (used by other services or for debugging)
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate JWT token", description = "Validates the JWT token in the Authorization header")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(
            @RequestHeader("Authorization") String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.error("Invalid Authorization header", null));
        }

        String token = authHeader.substring(7);
        boolean isBlacklisted = authService.isTokenBlacklisted(token);

        if (isBlacklisted) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Token has been invalidated", null));
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("valid", true, "message", "Token is valid"),
                "Token validated"));
    }
}
