package com.amazondemo.common.exception;

/**
 * Unauthorized Exception (HTTP 401)
 * Used when a user is not authenticated or token is invalid/expired.
 */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
