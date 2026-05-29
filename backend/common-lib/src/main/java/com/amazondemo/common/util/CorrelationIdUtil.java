package com.amazondemo.common.util;

import java.util.UUID;

/**
 * Correlation ID Utility
 * =======================
 * Correlation IDs are used to trace a single request as it flows through
 * multiple microservices. This is CRITICAL for distributed system debugging.
 *
 * HOW IT WORKS:
 * 1. API Gateway generates a unique ID for every incoming request
 * 2. The ID is passed as X-Correlation-ID header to downstream services
 * 3. Each service logs the correlation ID with every log statement
 * 4. When debugging, you can filter logs by correlation ID to see the full flow
 *
 * EXAMPLE FLOW:
 * Client -> API Gateway (gen ID: abc-123) -> Order Service (logs abc-123)
 *                                         -> Inventory Service (logs abc-123)
 *                                         -> Notification Service (logs abc-123)
 *
 * In logs, search "abc-123" to see everything that happened for that request.
 */
public class CorrelationIdUtil {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /**
     * Generates a new unique correlation ID
     */
    public static String generate() {
        return UUID.randomUUID().toString();
    }

    private CorrelationIdUtil() {
        // Utility class - no instantiation
    }
}
