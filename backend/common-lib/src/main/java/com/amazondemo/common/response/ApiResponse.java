package com.amazondemo.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standard API Response Wrapper
 * ================================
 * All APIs in the Amazon Demo application return responses wrapped in this object.
 *
 * WHY USE A WRAPPER?
 * - Consistent response format across all services
 * - Easy error handling on frontend/client side
 * - Includes metadata (timestamp, correlation ID, status)
 * - Industry standard - used in real enterprise applications
 *
 * USAGE:
 *   return ResponseEntity.ok(ApiResponse.success(data, "Products fetched successfully"));
 *   return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed", errors));
 *
 * @param <T> The type of data in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields in JSON
public class ApiResponse<T> {

    /** HTTP-like status: "SUCCESS" or "ERROR" */
    private String status;

    /** Human-readable message */
    private String message;

    /** The actual response data */
    private T data;

    /** Error details (only populated on errors) */
    private Object errors;

    /** Timestamp of the response - useful for debugging and auditing */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Correlation ID - traces a request across multiple services */
    private String correlationId;

    // ==================== FACTORY METHODS ====================

    /**
     * Creates a successful response with data
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates a successful response without data (e.g., DELETE operations)
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .status("SUCCESS")
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response
     */
    public static <T> ApiResponse<T> error(String message, Object errors) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .errors(errors)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Creates an error response with correlation ID for tracing
     */
    public static <T> ApiResponse<T> error(String message, Object errors, String correlationId) {
        return ApiResponse.<T>builder()
                .status("ERROR")
                .message(message)
                .errors(errors)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
