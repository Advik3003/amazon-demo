package com.amazondemo.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fallback Controller
 * ===================
 * Called by Circuit Breaker when a downstream service is unavailable.
 * Instead of showing an error, returns a friendly message.
 *
 * CIRCUIT BREAKER PATTERN:
 * - Normal: Gateway -> Service (works fine)
 * - Service down: After N failures, circuit "opens"
 * - Open circuit: Gateway immediately returns fallback response (no waiting)
 * - After timeout: Circuit "half-opens" and tries again
 *
 * This prevents cascade failures where one service failure brings down everything.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> authFallback() {
        return fallbackResponse("Auth Service is currently unavailable. Please try again later.");
    }

    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> userFallback() {
        return fallbackResponse("User Service is currently unavailable. Please try again later.");
    }

    @GetMapping("/product")
    public ResponseEntity<Map<String, Object>> productFallback() {
        return fallbackResponse("Product Service is currently unavailable. Please try again later.");
    }

    @GetMapping("/order")
    public ResponseEntity<Map<String, Object>> orderFallback() {
        return fallbackResponse("Order Service is currently unavailable. Please try again later.");
    }

    private ResponseEntity<Map<String, Object>> fallbackResponse(String message) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "ERROR",
                        "message", message,
                        "timestamp", LocalDateTime.now().toString()
                ));
    }
}
