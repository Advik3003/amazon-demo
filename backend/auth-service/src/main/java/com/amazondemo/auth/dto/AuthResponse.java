package com.amazondemo.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Auth Response DTO
 * Returned after successful login or token refresh
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private long expiresIn;  // seconds

    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
}
