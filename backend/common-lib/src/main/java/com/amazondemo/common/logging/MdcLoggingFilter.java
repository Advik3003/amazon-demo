package com.amazondemo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * MDC Logging Filter
 * ===================
 * Enriches every log line with request-scoped context for ELK correlation.
 *
 * WHAT IS MDC?
 * MDC (Mapped Diagnostic Context) is a thread-local key-value store that
 * Logback automatically includes in every log line produced on that thread.
 * This makes it possible to search Kibana for ALL logs from one specific
 * user, request, or correlation ID without any code changes in service classes.
 *
 * FIELDS ADDED TO MDC:
 *   requestId  : UUID generated per HTTP request (correlation across one call)
 *   userId     : Extracted from X-User-Id header (set by API Gateway after JWT validation)
 *   userEmail  : Extracted from X-User-Email header
 *   httpMethod : GET, POST, etc.
 *   requestUri : The URI being called
 *
 * TRACE FIELDS (added automatically by Micrometer Tracing + OTel):
 *   traceId    : Same across ALL services for a single distributed request
 *   spanId     : Unique per service hop (changes at each service boundary)
 *
 * KIBANA SEARCH EXAMPLES:
 *   traceId:"abc123"           → Show all logs from one distributed request
 *   userId:"user-001"          → Show all logs for one user
 *   requestId:"xyz789"         → Show all logs from one HTTP call
 *   service:"order-service" AND level:"ERROR"  → Errors in order service
 *
 * EXECUTION ORDER:
 *   @Order(Ordered.HIGHEST_PRECEDENCE) ensures this runs FIRST, before
 *   Spring Security filters, so tracing context is available in all filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USER_ID_HEADER    = "X-User-Id";
    public static final String USER_EMAIL_HEADER = "X-User-Email";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            // Generate or propagate request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            // Extract identity from API Gateway headers
            String userId    = request.getHeader(USER_ID_HEADER);
            String userEmail = request.getHeader(USER_EMAIL_HEADER);

            // Populate MDC - these appear in EVERY log line from this request
            MDC.put("requestId",   requestId);
            MDC.put("httpMethod",  request.getMethod());
            MDC.put("requestUri",  request.getRequestURI());

            if (userId != null && !userId.isBlank()) {
                MDC.put("userId", userId);
            }
            if (userEmail != null && !userEmail.isBlank()) {
                MDC.put("userEmail", userEmail);
            }

            // Propagate request ID in response for client-side correlation
            response.setHeader(REQUEST_ID_HEADER, requestId);

            filterChain.doFilter(request, response);

        } finally {
            // CRITICAL: Always clear MDC to prevent thread pool contamination
            MDC.clear();
        }
    }
}
