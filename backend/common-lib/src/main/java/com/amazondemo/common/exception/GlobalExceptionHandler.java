package com.amazondemo.common.exception;

import com.amazondemo.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler
 * =========================
 * Centralizes all exception handling for consistent error responses.
 *
 * HOW IT WORKS:
 * - @RestControllerAdvice intercepts exceptions thrown by any controller
 * - Each @ExceptionHandler method handles a specific exception type
 * - Always returns ApiResponse for consistency
 *
 * EXCEPTION HIERARCHY IN THIS APP:
 * Exception
 *   └── RuntimeException
 *         ├── ResourceNotFoundException (404)
 *         ├── BusinessException (400)
 *         ├── UnauthorizedException (401)
 *         └── ConflictException (409)
 *
 * INTERVIEW TIP: Always handle validation errors (MethodArgumentNotValidException)
 * and return field-level errors so the client knows exactly what went wrong.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid annotation failures - returns field-level errors
     * Example: {"username": "must not be blank", "email": "must be a valid email"}
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationErrors(
            MethodArgumentNotValidException ex, WebRequest request) {

        // Collect all field-level validation errors
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed for request: {} - Errors: {}", request.getDescription(false), errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errors));
    }

    /**
     * Handles resource not found (404)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex, WebRequest request) {

        log.warn("Resource not found: {} - Request: {}", ex.getMessage(), request.getDescription(false));

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    /**
     * Handles business logic violations (400)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {

        log.warn("Business exception: {} - Request: {}", ex.getMessage(), request.getDescription(false));

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    /**
     * Handles unauthorized access (401)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(
            UnauthorizedException ex, WebRequest request) {

        log.warn("Unauthorized access: {} - Request: {}", ex.getMessage(), request.getDescription(false));

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    /**
     * Handles duplicate resource conflicts (409)
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(
            ConflictException ex, WebRequest request) {

        log.warn("Conflict: {} - Request: {}", ex.getMessage(), request.getDescription(false));

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage(), null));
    }

    /**
     * Catch-all handler - handles any unexpected exceptions (500)
     * IMPORTANT: Never expose internal error details to the client in production
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {

        // Log the full stack trace for debugging (server-side only)
        log.error("Unexpected error occurred: {} - Request: {}", ex.getMessage(),
                request.getDescription(false), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An internal error occurred. Please try again later.", null));
    }
}
