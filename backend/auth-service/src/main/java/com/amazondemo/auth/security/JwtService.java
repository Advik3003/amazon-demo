package com.amazondemo.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * JWT Service
 * ============
 * Handles JWT token generation and validation.
 *
 * JWT STRUCTURE (3 parts separated by dots):
 * HEADER.PAYLOAD.SIGNATURE
 *
 * HEADER: {"alg": "HS256", "typ": "JWT"}
 * PAYLOAD: {
 *   "sub": "user-id-123",
 *   "email": "user@example.com",
 *   "roles": "ROLE_USER,ROLE_ADMIN",
 *   "iat": 1234567890,  <- issued at
 *   "exp": 1234568790   <- expires at
 * }
 * SIGNATURE: HMACSHA256(base64(header) + "." + base64(payload), secret)
 *
 * HOW VALIDATION WORKS:
 * 1. Re-compute the signature using our secret
 * 2. Compare with the signature in the token
 * 3. If they match, the token is authentic (not tampered with)
 * 4. Check expiry time
 *
 * SECURITY NOTE: The secret must be at least 256 bits (32 chars) for HS256.
 * In production, use environment variables or a secrets manager (AWS Secrets Manager).
 */
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret:amazondemo-super-secret-jwt-key-change-in-production-min-256-bits}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiry:900000}")  // 15 minutes
    private long accessTokenExpiry;

    @Value("${app.jwt.refresh-token-expiry:604800000}")  // 7 days
    private long refreshTokenExpiry;

    /**
     * Generate Access Token (short-lived JWT)
     * Includes user ID, email, and roles as claims
     */
    public String generateAccessToken(String userId, String email, Set<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);
        claims.put("roles", String.join(",", roles));

        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiry))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extract user ID from JWT token
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract email from JWT token
     */
    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get("email", String.class));
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Validate token - check signature and expiry
     */
    public boolean isTokenValid(String token) {
        try {
            parseAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract a specific claim from the token
     * @param token JWT token
     * @param claimsResolver function to extract a specific claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = parseAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parse all claims from the token
     * Throws exception if token is invalid or expired
     */
    public Claims parseAllClaims(String token) {
        return Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Get the signing key derived from the secret
     * Keys.hmacShaKeyFor() ensures minimum key length for HS256
     */
    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public long getAccessTokenExpiry() {
        return accessTokenExpiry;
    }
}
