package com.amazondemo.common.exception;

/**
 * Business Exception (HTTP 400)
 * Used for business rule violations.
 * Example: "Cannot cancel an order that is already delivered"
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
