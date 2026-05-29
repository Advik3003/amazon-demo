package com.amazondemo.auth.security;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService.
 *
 * Tests cover:
 * - Token generation with correct claims
 * - Claim extraction (userId, email, roles)
 * - Token validation (valid, expired, tampered)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    private static final String TEST_SECRET =
        "test-jwt-secret-key-for-unit-tests-min-256-bits-length-required";
    private static final String USER_ID = "user-001";
    private static final String USER_EMAIL = "test@example.com";
    private static final Set<String> USER_ROLES = Set.of("ROLE_USER");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", 900000L); // 15 min
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiry", 604800000L); // 7 days
    }

    // ==================== Token Generation ====================

    @Test
    @DisplayName("Should generate a valid access token")
    void shouldGenerateValidAccessToken() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        assertThat(token).isNotNull().isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    @DisplayName("Should include correct userId as subject in token")
    void shouldIncludeUserIdAsSubject() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        String extractedUserId = jwtService.extractUserId(token);

        assertThat(extractedUserId).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("Should include email claim in token")
    void shouldIncludeEmailInClaims() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        String extractedEmail = jwtService.extractEmail(token);

        assertThat(extractedEmail).isEqualTo(USER_EMAIL);
    }

    @Test
    @DisplayName("Should include roles claim in token")
    void shouldIncludeRolesInClaims() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        String rolesStr = jwtService.extractClaim(token,
            claims -> claims.get("roles", String.class));

        assertThat(rolesStr).contains("ROLE_USER");
    }

    @Test
    @DisplayName("Tokens for different users should be different")
    void shouldGenerateDifferentTokensForDifferentUsers() {
        String token1 = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);
        String token2 = jwtService.generateAccessToken("user-002", "other@example.com", USER_ROLES);

        assertThat(token1).isNotEqualTo(token2);
    }

    // ==================== Token Validation ====================

    @Test
    @DisplayName("Should return true for a valid, non-expired token")
    void shouldReturnTrueForValidToken() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isValid = jwtService.isTokenValid(token);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should return false for an expired token")
    void shouldReturnFalseForExpiredToken() {
        // Set very short expiry (1ms) to create an already-expired token
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", -1000L);
        String expiredToken = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isValid = jwtService.isTokenValid(expiredToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false for a tampered token")
    void shouldReturnFalseForTamperedToken() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);
        String tamperedToken = token.substring(0, token.lastIndexOf('.') + 1) + "invalidsignature";

        boolean isValid = jwtService.isTokenValid(tamperedToken);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false for completely invalid token string")
    void shouldReturnFalseForInvalidTokenString() {
        assertThat(jwtService.isTokenValid("not-a-jwt-at-all")).isFalse();
        assertThat(jwtService.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("Expired token should throw ExpiredJwtException when parsing claims")
    void shouldThrowExpiredJwtExceptionForExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiry", -1000L);
        String expiredToken = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        assertThatThrownBy(() -> jwtService.parseAllClaims(expiredToken))
            .isInstanceOf(ExpiredJwtException.class);
    }

    // ==================== Token Expiry ====================

    @Test
    @DisplayName("Should not be expired immediately after generation")
    void shouldNotBeExpiredImmediately() {
        String token = jwtService.generateAccessToken(USER_ID, USER_EMAIL, USER_ROLES);

        boolean isExpired = jwtService.isTokenExpired(token);

        assertThat(isExpired).isFalse();
    }

    @Test
    @DisplayName("Should report correct access token expiry")
    void shouldReturnCorrectAccessTokenExpiry() {
        assertThat(jwtService.getAccessTokenExpiry()).isEqualTo(900000L);
    }
}
