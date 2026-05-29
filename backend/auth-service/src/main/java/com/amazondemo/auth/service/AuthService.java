package com.amazondemo.auth.service;

import com.amazondemo.auth.dto.AuthResponse;
import com.amazondemo.auth.dto.LoginRequest;
import com.amazondemo.auth.dto.RegisterRequest;
import com.amazondemo.auth.model.RefreshToken;
import com.amazondemo.auth.model.User;
import com.amazondemo.auth.repository.RefreshTokenRepository;
import com.amazondemo.auth.repository.UserRepository;
import com.amazondemo.auth.security.JwtService;
import com.amazondemo.common.exception.BusinessException;
import com.amazondemo.common.exception.ConflictException;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.common.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Auth Service - Business Logic
 * ==============================
 * Contains all authentication operations.
 *
 * @RequiredArgsConstructor - Lombok generates constructor with all final fields
 * (this is the recommended way to do dependency injection in Spring)
 *
 * @Transactional - ensures database operations are atomic
 * If any step fails, all changes are rolled back
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;

    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:";
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * Register a new user
     * Steps:
     * 1. Check if email already exists
     * 2. Encrypt password with BCrypt
     * 3. Save user to database
     * 4. Generate JWT tokens
     * 5. Return auth response
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // Check for duplicate email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered: " + request.getEmail());
        }

        // Create user with encrypted password
        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of("ROLE_USER"))
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        return buildAuthResponse(savedUser);
    }

    /**
     * Login with email and password
     * Steps:
     * 1. Find user by email
     * 2. Check account not locked
     * 3. Verify password
     * 4. Reset failed attempts on success, increment on failure
     * 5. Generate tokens
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // Check account status
        if (!user.isEnabled()) {
            throw new BusinessException("Account is disabled. Please contact support.");
        }

        if (!user.isAccountNonLocked()) {
            throw new BusinessException("Account is locked due to too many failed attempts. Please contact support.");
        }

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Track failed attempts
            userRepository.incrementFailedAttempts(user.getId());

            int remaining = MAX_FAILED_ATTEMPTS - (user.getFailedLoginAttempts() + 1);
            if (remaining <= 0) {
                log.warn("Account locked due to too many failed attempts: {}", user.getEmail());
                throw new BusinessException("Account locked. Too many failed login attempts.");
            }

            throw new UnauthorizedException("Invalid email or password. " + remaining + " attempts remaining.");
        }

        // Successful login - reset failed attempts
        userRepository.resetFailedAttempts(user.getId());

        // Update last login timestamp
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("User logged in successfully: {}", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Refresh access token using a valid refresh token
     * Implements TOKEN ROTATION: old refresh token is revoked, new one is issued
     */
    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (!refreshToken.isValid()) {
            throw new UnauthorizedException("Refresh token has expired or been revoked. Please login again.");
        }

        User user = refreshToken.getUser();

        // TOKEN ROTATION: Revoke old refresh token
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        log.info("Token refreshed for user: {}", user.getId());
        return buildAuthResponse(user);
    }

    /**
     * Logout - blacklist the access token and revoke refresh token
     */
    @Transactional
    public void logout(String refreshTokenValue, String accessToken) {
        // Blacklist access token in Redis
        if (accessToken != null && !accessToken.isEmpty()) {
            try {
                // Get remaining TTL of the access token
                long expiry = jwtService.extractClaim(accessToken,
                        claims -> claims.getExpiration().getTime() - System.currentTimeMillis());

                if (expiry > 0) {
                    // Store in Redis with expiry matching token expiry
                    redisTemplate.opsForValue().set(
                            TOKEN_BLACKLIST_PREFIX + accessToken,
                            "blacklisted",
                            expiry,
                            TimeUnit.MILLISECONDS
                    );
                }
            } catch (Exception e) {
                log.warn("Could not blacklist access token: {}", e.getMessage());
            }
        }

        // Revoke refresh token
        if (refreshTokenValue != null && !refreshTokenValue.isEmpty()) {
            refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(rt -> {
                rt.setRevoked(true);
                refreshTokenRepository.save(rt);
            });
        }

        log.info("User logged out successfully");
    }

    /**
     * Check if an access token is blacklisted (called by API Gateway or other services)
     */
    public boolean isTokenBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_BLACKLIST_PREFIX + token));
    }

    // ==================== PRIVATE HELPERS ====================

    /**
     * Build auth response with new access and refresh tokens
     */
    private AuthResponse buildAuthResponse(User user) {
        // Generate access token
        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getEmail(), user.getRoles());

        // Generate and save refresh token
        String refreshTokenValue = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(refreshTokenValue)
                .user(user)
                .expiryDate(LocalDateTime.now().plusSeconds(
                        jwtService.getAccessTokenExpiry() / 1000 * 48 * 7))  // 7 days approx
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiry() / 1000)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .build();
    }
}
