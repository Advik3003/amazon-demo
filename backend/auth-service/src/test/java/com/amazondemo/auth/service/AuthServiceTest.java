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
import com.amazondemo.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AuthService.
 *
 * Tests all authentication flows:
 * - User registration (success, duplicate email)
 * - Login (success, wrong password, locked account)
 * - Token refresh (valid, expired, revoked)
 * - Logout (blacklist token, revoke refresh token)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private static final String MOCK_ACCESS_TOKEN = "mock.access.token";
    private static final String MOCK_REFRESH_TOKEN_VALUE = "mock-refresh-uuid";

    @BeforeEach
    void setUp() {
        activeUser = User.builder()
            .id("user-001")
            .email("john@example.com")
            .password("$2a$12$encodedPassword")
            .firstName("John")
            .lastName("Doe")
            .roles(Set.of("ROLE_USER"))
            .enabled(true)
            .accountNonLocked(true)
            .failedLoginAttempts(0)
            .build();

        when(jwtService.generateAccessToken(anyString(), anyString(), anySet()))
            .thenReturn(MOCK_ACCESS_TOKEN);
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ==================== REGISTER ====================

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("Should register new user and return auth response")
        void shouldRegisterSuccessfully() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("newuser@example.com");
            request.setPassword("Password123!");
            request.setFirstName("Jane");
            request.setLastName("Smith");

            when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
            when(passwordEncoder.encode("Password123!")).thenReturn("$2a$12$hashedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-new-001");
                return u;
            });

            AuthResponse response = authService.register(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo(MOCK_ACCESS_TOKEN);
            assertThat(response.getEmail()).isEqualTo("newuser@example.com");

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getRoles()).contains("ROLE_USER");
            assertThat(savedUser.getPassword()).isEqualTo("$2a$12$hashedPassword");
            assertThat(savedUser.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should throw ConflictException when email already exists")
        void shouldThrowConflictWhenEmailAlreadyExists() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("john@example.com");
            request.setPassword("Password123!");
            request.setFirstName("John");
            request.setLastName("Doe");

            when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should normalize email to lowercase on registration")
        void shouldNormalizeEmailToLowercase() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("JOHN@EXAMPLE.COM");
            request.setPassword("Password123!");
            request.setFirstName("John");
            request.setLastName("Doe");

            // Service calls existsByEmail with the original (uppercase) email, THEN lowercases before saving
            when(userRepository.existsByEmail("JOHN@EXAMPLE.COM")).thenReturn(false);
            when(userRepository.save(any())).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-001");
                return u;
            });

            authService.register(request);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("john@example.com");
        }
    }

    // ==================== LOGIN ====================

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with correct credentials")
        void shouldLoginSuccessfully() {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@example.com");
            request.setPassword("password123");

            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("password123", activeUser.getPassword()))
                .thenReturn(true);
            when(userRepository.save(any())).thenReturn(activeUser);

            AuthResponse response = authService.login(request);

            assertThat(response.getAccessToken()).isEqualTo(MOCK_ACCESS_TOKEN);
            assertThat(response.getUserId()).isEqualTo("user-001");
            assertThat(response.getEmail()).isEqualTo("john@example.com");
            verify(userRepository).resetFailedAttempts("user-001");
        }

        @Test
        @DisplayName("Should throw UnauthorizedException for non-existent email")
        void shouldThrowUnauthorizedForNonExistentEmail() {
            when(userRepository.findByEmail("ghost@example.com"))
                .thenReturn(Optional.empty());

            LoginRequest req = new LoginRequest();
            req.setEmail("ghost@example.com");
            req.setPassword("pass");
            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid email or password");
        }

        @Test
        @DisplayName("Should throw UnauthorizedException for wrong password")
        void shouldThrowUnauthorizedForWrongPassword() {
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongpassword", activeUser.getPassword()))
                .thenReturn(false);

            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("wrongpassword");
            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UnauthorizedException.class);

            verify(userRepository).incrementFailedAttempts("user-001");
        }

        @Test
        @DisplayName("Should throw BusinessException for disabled account")
        void shouldThrowForDisabledAccount() {
            activeUser.setEnabled(false);
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(activeUser));

            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("pass");
            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Account is disabled");
        }

        @Test
        @DisplayName("Should throw BusinessException for locked account")
        void shouldThrowForLockedAccount() {
            activeUser.setAccountNonLocked(false);
            when(userRepository.findByEmail("john@example.com"))
                .thenReturn(Optional.of(activeUser));

            LoginRequest req = new LoginRequest();
            req.setEmail("john@example.com");
            req.setPassword("pass");
            assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
        }
    }

    // ==================== REFRESH TOKEN ====================

    @Nested
    @DisplayName("refreshToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should issue new access token for valid refresh token")
        void shouldRefreshSuccessfully() {
            RefreshToken refreshToken = RefreshToken.builder()
                .token(MOCK_REFRESH_TOKEN_VALUE)
                .user(activeUser)
                .revoked(false)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

            when(refreshTokenRepository.findByToken(MOCK_REFRESH_TOKEN_VALUE))
                .thenReturn(Optional.of(refreshToken));
            when(refreshTokenRepository.save(any())).thenReturn(refreshToken);

            AuthResponse response = authService.refreshToken(MOCK_REFRESH_TOKEN_VALUE);

            assertThat(response.getAccessToken()).isEqualTo(MOCK_ACCESS_TOKEN);
            // Old token should be revoked (token rotation)
            assertThat(refreshToken.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("Should throw UnauthorizedException for invalid refresh token")
        void shouldThrowForInvalidRefreshToken() {
            when(refreshTokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("invalid-token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid refresh token");
        }

        @Test
        @DisplayName("Should throw UnauthorizedException for expired/revoked refresh token")
        void shouldThrowForExpiredRefreshToken() {
            RefreshToken expiredToken = RefreshToken.builder()
                .token(MOCK_REFRESH_TOKEN_VALUE)
                .user(activeUser)
                .revoked(true) // already revoked
                .expiryDate(LocalDateTime.now().minusDays(1)) // expired
                .build();

            when(refreshTokenRepository.findByToken(MOCK_REFRESH_TOKEN_VALUE))
                .thenReturn(Optional.of(expiredToken));

            assertThatThrownBy(() -> authService.refreshToken(MOCK_REFRESH_TOKEN_VALUE))
                .isInstanceOf(UnauthorizedException.class);
        }
    }

    // ==================== LOGOUT ====================

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Should blacklist access token in Redis on logout")
        void shouldBlacklistAccessTokenOnLogout() {
            String accessToken = "valid.access.token";
            long futureExpiry = System.currentTimeMillis() + 900000L;

            when(jwtService.extractClaim(eq(accessToken), any()))
                .thenReturn(futureExpiry - System.currentTimeMillis());
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            doNothing().when(valueOps).set(anyString(), anyString(), anyLong(), any());

            authService.logout(null, accessToken);

            verify(valueOps).set(
                eq("blacklist:" + accessToken),
                eq("blacklisted"),
                anyLong(),
                any()
            );
        }

        @Test
        @DisplayName("Should revoke refresh token in DB on logout")
        void shouldRevokeRefreshTokenOnLogout() {
            RefreshToken refreshToken = RefreshToken.builder()
                .token(MOCK_REFRESH_TOKEN_VALUE)
                .user(activeUser)
                .revoked(false)
                .build();

            when(refreshTokenRepository.findByToken(MOCK_REFRESH_TOKEN_VALUE))
                .thenReturn(Optional.of(refreshToken));

            authService.logout(MOCK_REFRESH_TOKEN_VALUE, null);

            assertThat(refreshToken.isRevoked()).isTrue();
            verify(refreshTokenRepository).save(refreshToken);
        }

        @Test
        @DisplayName("Should succeed gracefully when token values are null")
        void shouldHandleNullTokensGracefully() {
            assertThatCode(() -> authService.logout(null, null))
                .doesNotThrowAnyException();
        }
    }

    // ==================== TOKEN BLACKLIST CHECK ====================

    @Test
    @DisplayName("isTokenBlacklisted() should return true when token is in Redis")
    void shouldReturnTrueWhenTokenIsBlacklisted() {
        String token = "blacklisted.token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(true);

        assertThat(authService.isTokenBlacklisted(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenBlacklisted() should return false when token is not in Redis")
    void shouldReturnFalseWhenTokenIsNotBlacklisted() {
        String token = "valid.token";
        when(redisTemplate.hasKey("blacklist:" + token)).thenReturn(false);

        assertThat(authService.isTokenBlacklisted(token)).isFalse();
    }
}
