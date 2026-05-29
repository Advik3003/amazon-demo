package com.amazondemo.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

import java.io.IOException;

/**
 * Trace ID Propagation Interceptor
 * ==================================
 * Used with RestTemplate / Feign clients to propagate trace context
 * across service-to-service HTTP calls.
 *
 * WHY THIS IS NEEDED:
 * When Service A calls Service B, the traceId must be passed in the HTTP
 * request headers so Service B continues the same trace (instead of starting
 * a new one). This creates the "distributed trace" that Zipkin visualizes
 * as a single timeline across multiple services.
 *
 * NOTE: Spring Cloud OpenFeign with Micrometer Tracing does this automatically.
 * This interceptor is for plain RestTemplate clients.
 *
 * HEADERS PROPAGATED (W3C Trace Context standard):
 *   traceparent: 00-{traceId}-{spanId}-01
 *   X-B3-TraceId, X-B3-SpanId (Zipkin B3 format for compatibility)
 */
@Slf4j
public class TraceIdLoggingInterceptor implements ClientHttpRequestInterceptor {

    @Override
    @NonNull
    public ClientHttpResponse intercept(
            @NonNull HttpRequest request,
            @NonNull byte[] body,
            @NonNull ClientHttpRequestExecution execution) throws IOException {

        String traceId   = MDC.get("traceId");
        String spanId    = MDC.get("spanId");
        String requestId = MDC.get("requestId");

        if (traceId != null) {
            request.getHeaders().add("X-B3-TraceId", traceId);
        }
        if (spanId != null) {
            request.getHeaders().add("X-B3-SpanId", spanId);
        }
        if (requestId != null) {
            request.getHeaders().add(MdcLoggingFilter.REQUEST_ID_HEADER, requestId);
        }

        log.debug("Outgoing HTTP call to {} {} [traceId={}]",
                request.getMethod(), request.getURI(), traceId);

        return execution.execute(request, body);
    }
}
