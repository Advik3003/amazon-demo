package com.amazondemo.common.exception;

/**
 * Conflict Exception (HTTP 409)
 * Used when a resource already exists (e.g., duplicate email during registration).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
