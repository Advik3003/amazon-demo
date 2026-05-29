package com.amazondemo.auth.controller;

import com.amazondemo.auth.dto.AuthResponse;
import com.amazondemo.auth.dto.LoginRequest;
import com.amazondemo.auth.dto.RegisterRequest;
import com.amazondemo.auth.service.AuthService;
import com.amazondemo.common.exception.ConflictException;
import com.amazondemo.common.exception.GlobalExceptionHandler;
import com.amazondemo.common.exception.UnauthorizedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller (Web Layer) Tests for AuthController.
 *
 * Uses @WebMvcTest which loads only the web layer (no DB, no Kafka).
 * Tests HTTP request/response: status codes, JSON structure, validation.
 *
 * @WithMockUser disables CSRF and provides an authenticated principal for secured endpoints.
 * Auth endpoints (/register, /login) are public, so we add WithMockUser at class level
 * to satisfy CSRF protection that Spring Security adds by default in WebMvcTest.
 */
@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
@WithMockUser
@DisplayName("AuthController Web Layer Tests")
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private AuthService authService;

    private AuthResponse mockAuthResponse;

    @BeforeEach
    void setUp() {
        mockAuthResponse = AuthResponse.builder()
            .accessToken("mock.access.token")
            .refreshToken("mock-refresh-uuid")
            .tokenType("Bearer")
            .expiresIn(900L)
            .userId("user-001")
            .email("john@example.com")
            .firstName("John")
            .lastName("Doe")
            .roles(Set.of("ROLE_USER"))
            .build();
    }

    // ==================== REGISTER ====================

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class RegisterEndpointTests {

        @Test
        @DisplayName("Should return 201 and auth response for valid registration")
        void shouldReturn201ForValidRegistration() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("john@example.com");
            request.setPassword("Password123!");
            request.setFirstName("John");
            request.setLastName("Doe");

            when(authService.register(any())).thenReturn(mockAuthResponse);

            mockMvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("mock.access.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.email").value("john@example.com"))
                .andExpect(jsonPath("$.message").value("Registration successful"));
        }

        @Test
        @DisplayName("Should return 400 when email is missing")
        void shouldReturn400WhenEmailMissing() throws Exception {
            RegisterRequest invalidRequest = new RegisterRequest();
            invalidRequest.setPassword("Password123!");
            invalidRequest.setFirstName("John");
            invalidRequest.setLastName("Doe");

            mockMvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when password is too short")
        void shouldReturn400WhenPasswordTooShort() throws Exception {
            RegisterRequest invalidRequest = new RegisterRequest();
            invalidRequest.setEmail("john@example.com");
            invalidRequest.setPassword("123");
            invalidRequest.setFirstName("John");
            invalidRequest.setLastName("Doe");

            mockMvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 409 when email already exists")
        void shouldReturn409ForDuplicateEmail() throws Exception {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("john@example.com");
            request.setPassword("Password123!");
            request.setFirstName("John");
            request.setLastName("Doe");

            when(authService.register(any()))
                .thenThrow(new ConflictException("Email already registered"));

            mockMvc.perform(post("/api/v1/auth/register")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
        }
    }

    // ==================== LOGIN ====================

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class LoginEndpointTests {

        @Test
        @DisplayName("Should return 200 and auth tokens for valid credentials")
        void shouldReturn200ForValidLogin() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@example.com");
            request.setPassword("Password123!");

            when(authService.login(any())).thenReturn(mockAuthResponse);

            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.message").value("Login successful"));
        }

        @Test
        @DisplayName("Should return 401 for wrong password")
        void shouldReturn401ForWrongPassword() throws Exception {
            LoginRequest request = new LoginRequest();
            request.setEmail("john@example.com");
            request.setPassword("wrongpass");

            when(authService.login(any()))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should return 400 when request body is empty")
        void shouldReturn400WhenBodyIsEmpty() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== REFRESH TOKEN ====================

    @Nested
    @DisplayName("POST /api/v1/auth/refresh")
    class RefreshTokenEndpointTests {

        @Test
        @DisplayName("Should return 200 with new access token")
        void shouldReturn200ForValidRefreshToken() throws Exception {
            when(authService.refreshToken("valid-refresh-token"))
                .thenReturn(mockAuthResponse);

            mockMvc.perform(post("/api/v1/auth/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refreshToken\":\"valid-refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").value("mock.access.token"));
        }

        @Test
        @DisplayName("Should return 400 when refreshToken field is missing")
        void shouldReturn400WhenRefreshTokenMissing() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    // ==================== LOGOUT ====================

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class LogoutEndpointTests {

        @Test
        @DisplayName("Should return 200 on successful logout")
        void shouldReturn200OnLogout() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post("/api/v1/auth/logout")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer valid.token.here")
                    .content("{\"refreshToken\":\"refresh-token-value\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        }
    }
}
