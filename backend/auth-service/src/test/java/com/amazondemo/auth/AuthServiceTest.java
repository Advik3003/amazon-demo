package com.amazondemo.auth;

import com.amazondemo.auth.dto.LoginRequest;
import com.amazondemo.auth.dto.RegisterRequest;
import com.amazondemo.auth.model.User;
import com.amazondemo.auth.repository.RefreshTokenRepository;
import com.amazondemo.auth.repository.UserRepository;
import com.amazondemo.auth.security.JwtService;
import com.amazondemo.auth.service.AuthService;
import com.amazondemo.common.exception.ConflictException;
import com.amazondemo.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Auth Service Unit Tests
 * ========================
 *
 * UNIT TESTING CONCEPTS:
 * - Unit tests test a single class/method in ISOLATION
 * - Dependencies are MOCKED - we don't actually call the database or Redis
 * - Fast to run (no external dependencies)
 * - Tests business logic only
 *
 * MOCKITO:
 * - @Mock creates a mock (fake) object
 * - @InjectMocks creates the class under test and injects mocks
 * - when(...).thenReturn(...) defines mock behavior
 * - verify(...) checks that a method was called
 *
 * JUNIT 5:
 * - @Test marks a test method
 * - @BeforeEach runs before each test
 * - @DisplayName gives a human-readable test name
 * - assertThat() provides fluent assertions
 *
 * WHY WRITE TESTS?
 * - Catch bugs early before they reach production
 * - Serve as documentation (tests describe expected behavior)
 * - Enable confident refactoring
 * - Required in professional environments
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth Service Tests")
class AuthServiceTest {

    // Mocks - fake implementations, no real DB calls
    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private StringRedisTemplate redisTemplate;

    // The class we're actually testing
    @InjectMocks
    private AuthService authService;

    private User testUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        // Create test data used across multiple tests
        testUser = User.builder()
                .id("user-123")
                .email("test@example.com")
                .password("$2a$12$encodedPassword")
                .firstName("John")
                .lastName("Doe")
                .roles(Set.of("ROLE_USER"))
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .failedLoginAttempts(0)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("John");
        registerRequest.setLastName("Doe");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("test@example.com");
        loginRequest.setPassword("password123");
    }

    // ==================== REGISTRATION TESTS ====================

    @Test
    @DisplayName("Should register a new user successfully")
    void register_WithValidData_ShouldSucceed() {
        // ARRANGE - set up mocks
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(null);

        // ACT - call the method under test
        var response = authService.register(registerRequest);

        // ASSERT - verify the result
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getEmail()).isEqualTo("test@example.com");

        // Verify password was encoded (never store plain text!)
        verify(passwordEncoder).encode("password123");

        // Verify user was saved
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw ConflictException when email already exists")
    void register_WithExistingEmail_ShouldThrowConflict() {
        // ARRANGE
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        // ACT + ASSERT - expect exception to be thrown
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email already registered");

        // Verify user was NOT saved (early return on conflict)
        verify(userRepository, never()).save(any());
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("Should login successfully with correct credentials")
    void login_WithValidCredentials_ShouldSucceed() {
        // ARRANGE
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateAccessToken(anyString(), anyString(), any())).thenReturn("access-token");
        when(jwtService.getAccessTokenExpiry()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(null);

        // ACT
        var response = authService.login(loginRequest);

        // ASSERT
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getUserId()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException when user not found")
    void login_WithNonExistentEmail_ShouldThrowUnauthorized() {
        // ARRANGE - user doesn't exist
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    @DisplayName("Should throw UnauthorizedException on wrong password")
    void login_WithWrongPassword_ShouldThrowUnauthorized() {
        // ARRANGE
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(UnauthorizedException.class);

        // Verify failed attempts were incremented
        verify(userRepository).incrementFailedAttempts(testUser.getId());
    }

    @Test
    @DisplayName("Should throw BusinessException when account is disabled")
    void login_WithDisabledAccount_ShouldThrowBusiness() {
        // ARRANGE
        User disabledUser = User.builder()
                .id("user-456")
                .email("disabled@example.com")
                .password("$2a$12$encoded")
                .enabled(false)  // Account disabled
                .accountNonLocked(true)
                .build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(disabledUser));

        // ACT + ASSERT
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(com.amazondemo.common.exception.BusinessException.class)
                .hasMessageContaining("disabled");
    }
}
