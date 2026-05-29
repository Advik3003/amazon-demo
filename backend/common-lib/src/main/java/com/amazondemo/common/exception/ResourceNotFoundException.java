package com.amazondemo.common.exception;

/**
 * Resource Not Found Exception (HTTP 404)
 * Used when a requested resource doesn't exist in the database.
 * Example: Product with id=999 not found
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
