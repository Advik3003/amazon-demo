package com.amazondemo.user.controller;

import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.user.dto.AddressDto;
import com.amazondemo.user.dto.UserProfileDto;
import com.amazondemo.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * User Profile Controller
 * ========================
 * All endpoints require authentication (JWT token validated at API Gateway).
 * The user's ID is extracted from the X-User-Id header (set by gateway).
 *
 * SECURITY NOTE: We trust X-User-Id from the gateway (it validates JWT).
 * Never trust user IDs from request bodies - users could impersonate others.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Profiles", description = "User profile and address management")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * POST /api/v1/users/profile
     * Create a user profile (called after registration)
     */
    @PostMapping("/profile")
    @Operation(summary = "Create user profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> createProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Email") String email,
            @RequestBody Map<String, String> request) {

        UserProfileDto profile = userProfileService.createProfile(
                userId, email,
                request.get("firstName"),
                request.get("lastName")
        );
        return ResponseEntity.ok(ApiResponse.success(profile, "Profile created"));
    }

    /**
     * GET /api/v1/users/profile
     * Get the authenticated user's profile
     */
    @GetMapping("/profile")
    @Operation(summary = "Get current user's profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> getProfile(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.success(userProfileService.getProfile(userId), "Profile fetched"));
    }

    /**
     * GET /api/v1/users/{id}
     * Get any user's profile (admin use or internal service calls)
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get user profile by ID")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(
                ApiResponse.success(userProfileService.getProfile(id), "User fetched"));
    }

    /**
     * PUT /api/v1/users/profile
     * Update authenticated user's profile
     */
    @PutMapping("/profile")
    @Operation(summary = "Update user profile")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateProfile(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody UserProfileDto updateRequest) {
        return ResponseEntity.ok(
                ApiResponse.success(userProfileService.updateProfile(userId, updateRequest),
                        "Profile updated"));
    }

    /**
     * POST /api/v1/users/addresses
     * Add a new address
     */
    @PostMapping("/addresses")
    @Operation(summary = "Add a new address")
    public ResponseEntity<ApiResponse<AddressDto>> addAddress(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody AddressDto addressDto) {
        return ResponseEntity.ok(
                ApiResponse.success(userProfileService.addAddress(userId, addressDto),
                        "Address added"));
    }

    /**
     * GET /api/v1/users/addresses
     * Get all addresses for the authenticated user
     */
    @GetMapping("/addresses")
    @Operation(summary = "Get all user addresses")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddresses(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(
                ApiResponse.success(userProfileService.getAddresses(userId),
                        "Addresses fetched"));
    }
}
