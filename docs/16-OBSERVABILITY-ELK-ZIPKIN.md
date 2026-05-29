# Distributed Observability: ELK Stack + Zipkin + OpenTelemetry

> **Tutorial Level**: Senior Engineer  
> **Interview Topics**: Distributed tracing, centralized logging, OpenTelemetry, observability pillars  
> **Time to Complete**: 45 minutes  

---

## Table of Contents

1. [The Three Pillars of Observability](#1-three-pillars)
2. [Architecture Overview](#2-architecture-overview)
3. [ELK vs Zipkin: What They Do and Why You Need BOTH](#3-elk-vs-zipkin)
4. [OpenTelemetry: The Unified Standard](#4-opentelemetry)
5. [Implementation Deep Dive](#5-implementation)
6. [Logback JSON Configuration Explained](#6-logback)
7. [Running the Observability Stack](#7-running)
8. [Kibana: Setting Up and Querying Logs](#8-kibana)
9. [Zipkin: Tracing Distributed Requests](#9-zipkin)
10. [Correlating Logs and Traces](#10-correlation)
11. [Production Considerations](#11-production)
12. [Interview Q&A](#12-interview-qa)

---

## 1. Three Pillars of Observability {#1-three-pillars}

Modern distributed systems require **observability** — the ability to understand the internal state of a system from its external outputs. There are three pillars:

```
┌─────────────────────────────────────────────────────────────────────┐
│                    THREE PILLARS OF OBSERVABILITY                    │
├─────────────┬──────────────────────────────┬───────────────────────┤
│   LOGS      │   TRACES                     │   METRICS             │
│             │                              │                       │
│ "What       │ "What path did this          │ "How is the system    │
│  happened?" │  request take?"              │  behaving over time?" │
│             │                              │                       │
│ ELK Stack   │ Zipkin / Jaeger              │ Prometheus + Grafana  │
│ (this doc)  │ (this doc)                   │ (separate topic)      │
│             │                              │                       │
│ Full-text   │ Request timeline across      │ CPU, memory, request  │
│ searchable  │ multiple services            │ rate, error rate      │
│ log archive │                              │ (RED metrics)         │
└─────────────┴──────────────────────────────┴───────────────────────┘
```

**Key Insight**: These three pillars are **complementary, not redundant**.  
- You look at **metrics** first: "Error rate spiked at 14:32"  
- Then check **traces**: "These 500 errors originated in order-service"  
- Then read **logs**: "NullPointerException in OrderService.java:145"

---

## 2. Architecture Overview {#2-architecture-overview}

```
╔═══════════════════════════════════════════════════════════════════════╗
║                    OBSERVABILITY ARCHITECTURE                          ║
╠═══════════════════════════════════════════════════════════════════════╣
║                                                                        ║
║  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   ║
║  │  API    │  │  Auth   │  │ Product │  │  Order  │  │ Payment │   ║
║  │ Gateway │  │ Service │  │ Service │  │ Service │  │ Service │   ║
║  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   ║
║       │             │            │             │             │        ║
║       └─────────────┴────────────┴─────────────┴─────────────┘       ║
║                               │                                       ║
║              ┌────────────────┼────────────────┐                     ║
║              │                │                │                     ║
║              ▼                ▼                │                     ║
║   ┌──────────────────┐  ┌──────────┐           │                     ║
║   │   LOGSTASH       │  │  ZIPKIN  │           │                     ║
║   │  (TCP port 5000) │  │ (port    │           │                     ║
║   │  JSON log intake │  │  9411)   │           │                     ║
║   └────────┬─────────┘  └──────────┘           │                     ║
║            │            Span/trace receiver     │                     ║
║            ▼                                    │                     ║
║   ┌──────────────────┐                          │                     ║
║   │  ELASTICSEARCH   │◄─────────────────────────┘                    ║
║   │  (port 9200)     │   Logstash writes JSON logs                   ║
║   │  JSON log store  │   by service + date index                     ║
║   └────────┬─────────┘                                               ║
║            │                                                         ║
║            ▼                                                         ║
║   ┌──────────────────┐                                               ║
║   │    KIBANA        │                                               ║
║   │  (port 5601)     │                                               ║
║   │  Log dashboards  │                                               ║
║   └──────────────────┘                                               ║
╚═══════════════════════════════════════════════════════════════════════╝

DATA FLOWS:
  ① HTTP Request arrives at API Gateway
  ② OTel creates a new trace (traceId) and span (spanId)
  ③ traceId/spanId injected into MDC (thread-local context)
  ④ All log lines include traceId + spanId automatically
  ⑤ JSON logs shipped to Logstash via TCP (async, non-blocking)
  ⑥ Logstash routes to Elasticsearch index: amazondemo-{service}-{date}
  ⑦ OTel exports spans to Zipkin every N seconds
  ⑧ In Kibana: search by traceId to see all logs for one request
  ⑨ In Zipkin: paste traceId to see the full distributed trace timeline
```

---

## 3. ELK vs Zipkin: What They Do and Should You Use Both? {#3-elk-vs-zipkin}

> **Interview Question**: "Should you use ELK and Zipkin together in the same project?"  
> **Answer**: **YES — absolutely. They serve fundamentally different purposes.**

### Side-by-Side Comparison

| Dimension          | ELK Stack (Elasticsearch + Logstash + Kibana) | Zipkin                                  |
|--------------------|-----------------------------------------------|-----------------------------------------|
| **What it stores** | All log lines: INFO, WARN, ERROR, DEBUG       | Distributed spans (start time, end time, service name) |
| **Query type**     | Full-text search, regex, aggregations         | Trace ID → timeline view                |
| **Use case**       | "Find all ERROR logs in auth-service today"   | "Show me the full request flow when user X placed order Y" |
| **Data volume**    | Very high (1000s of logs per request)         | Low (1 span per service hop per request)|
| **Retention**      | Long (30-90 days typical)                     | Short (7-14 days typical)               |
| **When to use**    | Post-incident debugging, compliance auditing  | Performance profiling, latency analysis |
| **Answers**        | "What exactly happened?"                      | "Which service was slow?"               |

### How They Complement Each Other

```
SCENARIO: Order placement fails for user@example.com

STEP 1 — Zipkin:
  Search by traceId "abc123"
  ↓ See timeline:
    api-gateway  →  order-service  →  inventory-service
    [   2ms    ]    [  450ms    ]    [    3200ms    ] ← SLOW!
  ↓ inventory-service took 3.2 seconds — something's wrong there

STEP 2 — Kibana:
  Filter: service="inventory-service" AND traceId="abc123"
  ↓ Find the exact log lines:
    WARN  [abc123, def456] Inventory lock timeout after 3000ms
    ERROR [abc123, def456] Could not acquire pessimistic lock for product prod-001
  ↓ Root cause: database lock contention in inventory service

WITHOUT ZIPKIN: You'd have to manually trace the path across 5 services
WITHOUT ELK:    You'd see the slow span but not WHY it was slow
TOGETHER:       Complete picture in 30 seconds
```

### When You Might Only Use One

- **Only ELK**: Small monolith with one service, no cross-service tracing needed
- **Only Zipkin**: You care only about latency/performance, not log details

**In a microservices system, always use both.**

---

## 4. OpenTelemetry: The Unified Standard {#4-opentelemetry}

**OpenTelemetry (OTel)** is a CNCF standard that provides vendor-neutral APIs and SDKs for distributed tracing, metrics, and logging.

### Why OTel Matters

Before OTel, every tracing backend (Zipkin, Jaeger, Datadog, New Relic) had its own SDK. Switching from Zipkin to Jaeger meant rewriting all instrumentation code.

With OTel:
```
Your Code → OTel SDK → OTel Exporter → Any Backend
                    ↗ Zipkin Exporter  → Zipkin
                    ↗ Jaeger Exporter  → Jaeger  
                    ↗ OTLP Exporter    → Datadog / New Relic / Grafana Tempo
```

You write instrumentation code **once** and switch backends by changing configuration.

### Spring Boot + Micrometer + OTel Stack

```
Spring Boot App
    │
    ▼ (auto-instrumentation)
Micrometer Tracing API  (Spring's tracing facade - vendor neutral)
    │
    ▼ (bridge)
micrometer-tracing-bridge-otel  (translates Micrometer → OTel)
    │
    ▼
OpenTelemetry SDK  (OTel's trace management)
    │
    ▼
opentelemetry-exporter-zipkin  (formats spans as Zipkin JSON and HTTP POSTs them)
    │
    ▼
Zipkin  (stores and visualizes)
```

### Trace Context Propagation (W3C Standard)

When Service A calls Service B, the trace context travels in HTTP headers:

```
GET /api/v1/inventory/check
traceparent: 00-abc123def456-span001-01
             └─ traceId  ──┘ └─spanId─┘
```

OTel automatically:
1. **Creates** the header when making outbound requests
2. **Reads** the header on incoming requests to continue the trace
3. **Injects** traceId into MDC so Logback includes it in every log line

---

## 5. Implementation Deep Dive {#5-implementation}

### 5.1 Maven Dependencies (Parent POM)

```xml
<!-- pom.xml (parent) -->

<!-- Distributed Tracing: Micrometer → OpenTelemetry → Zipkin -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
    <!-- Version managed by Spring Boot BOM -->
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
    <!-- Version managed by Spring Boot BOM -->
</dependency>

<!-- Structured Logging: JSON format for ELK ingestion -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

**Why these specific libraries?**
- `micrometer-tracing-bridge-otel`: Spring Boot uses Micrometer Tracing as its tracing API. This library bridges it to the OTel implementation — you write Micrometer code, OTel does the work.
- `opentelemetry-exporter-zipkin`: The final mile — exports collected spans to Zipkin's HTTP API.
- `logstash-logback-encoder`: Replaces Logback's default text encoder with JSON. Required for ELK — Logstash can parse JSON but not arbitrary text formats reliably.

### 5.2 Application Configuration

```yaml
# application.yml (all services)

management:
  tracing:
    enabled: true
    sampling:
      probability: 1.0      # 100% in dev; 0.1 (10%) in production

  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
      # In Docker: http://zipkin:9411/api/v2/spans

# Logstash connection (read by logback-spring.xml)
logstash:
  host: localhost            # In Docker: logstash
  port: 5000
```

**Sampling Rate** is critical in production:
- `1.0` = 100% of requests traced (dev/staging only — expensive)
- `0.1` = 10% of requests traced (production standard)
- `0.01` = 1% of requests traced (high-volume systems, e.g., >10k req/s)

### 5.3 MDC Logging Filter

Every incoming HTTP request gets enriched MDC (Mapped Diagnostic Context):

```java
// MdcLoggingFilter.java (in common-lib, inherited by all services)

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // Runs BEFORE Spring Security
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, ...) {
        try {
            // requestId: unique per HTTP call (for correlation even without tracing)
            String requestId = getOrGenerate(request.getHeader("X-Request-Id"));
            
            // userId: set by API Gateway after JWT validation
            String userId = request.getHeader("X-User-Id");
            
            MDC.put("requestId",  requestId);
            MDC.put("userId",     userId);     // null-safe, not set if empty
            MDC.put("httpMethod", request.getMethod());
            MDC.put("requestUri", request.getRequestURI());
            
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // CRITICAL: always clear, or thread pool leaks context
        }
    }
}
```

**Why `@Order(Ordered.HIGHEST_PRECEDENCE)`?**  
This filter must run BEFORE Spring Security filters so that all security-related log lines also have the MDC context populated.

**Why `MDC.clear()` in `finally`?**  
Spring uses a thread pool. After request completion, the thread returns to the pool and handles the next request. Without clearing, MDC from request A would appear in request B's logs.

---

## 6. Logback JSON Configuration Explained {#6-logback}

### logback-spring.xml Structure

```xml
<configuration>

  <!-- Read from application.yml -->
  <springProperty name="SERVICE_NAME" source="spring.application.name"/>
  <springProperty name="LOGSTASH_HOST" source="logstash.host" defaultValue="localhost"/>
  
  <!-- Appender 1: Human-readable console (dev) -->
  <appender name="CONSOLE" class="ConsoleAppender">
    <pattern>%d{HH:mm:ss} [%highlight(%-5level)] [trace=%X{traceId}] %logger{36} - %msg%n</pattern>
  </appender>
  
  <!-- Appender 2: JSON TCP to Logstash -->
  <appender name="LOGSTASH" class="LogstashTcpSocketAppender">
    <destination>${LOGSTASH_HOST}:5000</destination>
    <encoder class="LogstashEncoder">
      <!-- Adds service/environment to every log line -->
      <customFields>{"service":"${SERVICE_NAME}","environment":"${ACTIVE_PROFILE}"}</customFields>
      <!-- Include MDC fields (traceId added by Micrometer automatically) -->
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
    </encoder>
  </appender>
  
  <!-- Appender 3: Async wrapper (never block request threads on log I/O) -->
  <appender name="ASYNC_LOGSTASH" class="AsyncAppender">
    <appender-ref ref="LOGSTASH"/>
    <queueSize>512</queueSize>
    <neverBlock>true</neverBlock>  <!-- Drop logs if queue full -->
  </appender>
  
  <!-- Profile-based root: test=console only, local=console+logstash -->
  <springProfile name="test">
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
    </root>
  </springProfile>
  
  <springProfile name="local,default">
    <root level="INFO">
      <appender-ref ref="CONSOLE"/>
      <appender-ref ref="ASYNC_LOGSTASH"/>
    </root>
  </springProfile>
  
</configuration>
```

### What a Log Line Looks Like in Elasticsearch

```json
{
  "@timestamp": "2026-05-29T11:32:15.847Z",
  "level": "INFO",
  "service": "order-service",
  "environment": "local",
  "thread": "http-nio-8085-exec-3",
  "logger": "com.amazondemo.order.service.OrderService",
  "message": "Order created: ORD-2026-001234 for user user-001",
  "traceId": "4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c",
  "spanId": "1a2b3c4d5e6f7a8b",
  "parentId": "9b8a7c6d5e4f3a2b",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-001",
  "httpMethod": "POST",
  "requestUri": "/api/v1/orders"
}
```

Every field is indexed by Elasticsearch — you can filter by any field instantly.

### Async Appender: Why It Matters

```
Without AsyncAppender:
  Request thread → log message → TCP write to Logstash → wait → continue
  (If Logstash is slow or down, requests slow down or fail)

With AsyncAppender:
  Request thread → log message → put in queue → continue (instantly)
  Background thread → dequeue → TCP write to Logstash
  (Request thread never blocks on logging I/O)
```

`neverBlock: true` means if the 512-item queue is full, the log is **dropped** rather than blocking the request. In production under extreme load, it's better to lose some logs than to impact user-facing response times.

---

## 7. Running the Observability Stack {#7-running}

### Quick Start

```bash
# 1. Start only the observability infrastructure
docker compose up -d elasticsearch logstash kibana zipkin

# 2. Wait for Elasticsearch to be healthy (takes ~60 seconds)
docker compose ps elasticsearch
# Status should show "healthy"

# 3. Start your Spring Boot services
docker compose up -d auth-service order-service product-service

# 4. Generate some traffic
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"Test1234!","firstName":"Test","lastName":"User"}'

# 5. Open observability UIs
open http://localhost:5601   # Kibana
open http://localhost:9411   # Zipkin
```

### Verify Logstash is Receiving Logs

```bash
# Check Logstash pipeline stats
curl http://localhost:9600/_node/stats/pipelines?pretty

# Check what indices Elasticsearch has created
curl http://localhost:9200/_cat/indices?v

# Expected output:
# health status index                            
# green  open   amazondemo-auth-service-2026.05.29
# green  open   amazondemo-order-service-2026.05.29
```

### Verify Zipkin is Receiving Traces

```bash
# Check Zipkin has services registered
curl http://localhost:9411/api/v2/services

# Expected: ["auth-service","order-service","product-service","api-gateway"]
```

---

## 8. Kibana: Setting Up and Querying Logs {#8-kibana}

### Step 1: Create a Data View

1. Open `http://localhost:5601`
2. Navigate to **Stack Management** → **Data Views**
3. Click **Create data view**
4. Set:
   - **Name**: `Amazon Demo - All Services`
   - **Index pattern**: `amazondemo-*`
   - **Timestamp field**: `@timestamp`
5. Click **Save data view**

### Step 2: Explore Logs in Discover

Navigate to **Discover** (the compass icon).

**Essential Kibana Queries (KQL syntax):**

```kql
# ---- BASIC FILTERS ----

# Show all ERROR logs
level: "ERROR"

# Show logs from one service
service: "order-service"

# Show logs from one request (distributed trace)
traceId: "abc123def456"

# Show logs for one user
userId: "user-001"

# ---- COMBINING FILTERS ----

# Errors in order-service in last 1 hour
service: "order-service" AND level: "ERROR"

# Trace a specific order creation
traceId: "abc123" AND message: "Order"

# Errors not in infrastructure
level: "ERROR" AND NOT logger: "org.springframework"

# ---- PERFORMANCE QUERIES ----

# Find slow database queries (if logged)
message: "*ms*" AND level: "WARN"

# Payment failures
service: "payment-service" AND message: "Payment failed"

# ---- TIME RANGE ----
# Use the time picker in top-right of Kibana UI
# Common: "Last 15 minutes", "Today", "Last 7 days"
```

### Step 3: Create a Log Dashboard

1. Go to **Dashboards** → **Create dashboard**

**Useful Panels:**

| Panel Type | Metric | Use |
|------------|--------|-----|
| Metric | Count of ERROR level | Total errors in time range |
| Bar chart | level by service | Error distribution across services |
| Data table | Top logger values | Which classes log the most |
| Line chart | Count over time | Log volume trend |
| Data table | service + traceId | Trace lookup table |

---

## 9. Zipkin: Tracing Distributed Requests {#9-zipkin}

### How Zipkin Collects Spans

```
User → API Gateway → Order Service → Inventory Service

Each service records a "span":
  span 1: api-gateway     start:0ms   end:5ms    (routing)
  span 2: order-service   start:5ms   end:450ms  (business logic)
  span 3: inventory-svc   start:10ms  end:405ms  (stock check)

All spans share the SAME traceId.
Zipkin assembles them into a timeline (Gantt chart).
```

### Using Zipkin UI

1. Open `http://localhost:9411`

2. **Find Traces** tab:
   - **Service**: `order-service`
   - **Span Name**: `POST /api/v1/orders`
   - **Duration**: `> 500ms` (find slow requests)
   - Click **Run Query**

3. Click a trace → see the full span timeline:

```
[api-gateway]     ████░░░░░░░░░░░░░░░░░░   5ms
  [order-service]  ░████████████████████░  445ms  ← root cause
    [inventory]    ░░░░░░███████████████░  400ms  ← slow child
```

4. Click any span → see tags:
   - `http.method`: POST
   - `http.url`: http://inventory-service/api/v1/inventory/reserve
   - `http.status_code`: 200
   - `error`: true/false

### Command-Line Zipkin Queries

```bash
# Get all services
curl http://localhost:9411/api/v2/services

# Get all spans for a service
curl "http://localhost:9411/api/v2/spans?serviceName=order-service"

# Find traces by service and min duration (500ms)
curl "http://localhost:9411/api/v2/traces?serviceName=order-service&minDuration=500000"

# Find a specific trace by traceId
curl "http://localhost:9411/api/v2/trace/{traceId}"

# Get dependency graph between services
curl http://localhost:9411/api/v2/dependencies?endTs=1717000000000
```

---

## 10. Correlating Logs and Traces {#10-correlation}

The **traceId** is the golden thread that links Zipkin and Kibana.

### Workflow: Investigating a Production Issue

```
1. User reports: "My order placement failed at 2:35 PM"

2. Zipkin:
   - Filter: service=api-gateway, time=14:35, status=error
   - Find trace: traceId = "4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c"
   - See: api-gateway → order-service → inventory-service
   - inventory-service span: ERROR, duration=3200ms

3. Kibana:
   - KQL: traceId: "4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c"
   - See all log lines from ALL services for this one request
   - Sorted by @timestamp shows exact sequence
   - ERROR line: "Database connection timeout in InventoryRepository.reserve()"
   - Stack trace included in the log

4. Root cause identified in < 2 minutes:
   - inventory-service database connection pool was exhausted
   - PostgreSQL connection was waiting 3 seconds before timeout

5. Fix: increase HikariCP pool size from 5 to 20
```

### Making traceId Visible to End Users

Add traceId to API responses so support teams can look it up:

```java
// In GlobalExceptionHandler or ResponseBodyAdvice
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<?>> handleError(Exception ex) {
    String traceId = MDC.get("traceId");  // Already set by Micrometer
    
    return ResponseEntity.status(500).body(
        ApiResponse.error("Internal error. Reference: " + traceId, "INTERNAL_ERROR")
    );
}
```

The user sees: `"message": "Internal error. Reference: 4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c"`  
Support team pastes this into Kibana filter → instant log lookup.

---

## 11. Production Considerations {#11-production}

### Log Volume and Retention

```
Average log volume per service: ~500 lines/second under load
At 10 services × 500 lines × 200 bytes = ~1MB/second = ~86GB/day

Recommendations:
  - Set Elasticsearch ILM (Index Lifecycle Management):
    Hot (0-3 days): SSD, full indexing
    Warm (4-14 days): HDD, read-only
    Cold (15-30 days): Compressed, minimal indexing
    Delete (>30 days): Auto-delete

  - Set log levels per environment:
    local/test: DEBUG
    staging:    INFO
    prod:       WARN (services) + INFO (your own packages)
```

### Sampling in Production

```yaml
# production profile
management:
  tracing:
    sampling:
      probability: 0.05   # 5% sampling
      # For 1000 req/s: 50 traces/s sent to Zipkin
      # Zipkin storage: ~50 × 5 spans × 500 bytes = 125KB/s manageable
```

For critical paths (payments, auth), use 100% sampling:
```java
@NewSpan  // Force a new span (auto-sampled at 100%)
public PaymentResult processPayment(PaymentRequest req) { ... }
```

### Security: Log Sanitization

**Never log sensitive data:**

```java
// BAD:
log.info("Processing payment card: {}", cardNumber);
log.debug("User password hash: {}", passwordHash);
log.info("JWT token: {}", token);

// GOOD:
log.info("Processing payment for order: {} (card ending {})", orderId, last4Digits);
log.debug("User credentials validated for: {}", email);
log.info("Authentication successful for: {}", email);
```

Add a log sanitization filter in production to mask PII:
- Credit card numbers → `****-****-****-1234`
- Email addresses → `t***@example.com`
- JWT tokens → `<JWT_REDACTED>`

### High Availability ELK

For production ELK:
```yaml
# Elasticsearch cluster (3 nodes minimum)
elasticsearch:
  cluster.name: amazon-demo-prod
  cluster.initial_master_nodes: ["es1","es2","es3"]
  discovery.seed_hosts: ["es1","es2","es3"]

# Logstash pipeline redundancy
logstash:
  pipeline.workers: 4          # Multiple threads
  queue.type: persisted        # Disk queue (survives crashes)
  queue.max_bytes: 4gb
  dead_letter_queue.enable: true  # Re-process failed events
```

---

## 12. Interview Q&A {#12-interview-qa}

---

### Q1: "What is OpenTelemetry and why does it matter?"

**Answer:**  
OpenTelemetry (OTel) is a CNCF vendor-neutral standard for distributed observability. Before OTel, every tracing backend (Zipkin, Jaeger, Datadog) had its own SDK — switching backends meant rewriting instrumentation code.

OTel defines:
- **API**: How you write instrumentation code (vendor-neutral)
- **SDK**: The implementation that collects telemetry
- **Exporters**: Plugins that send data to specific backends (Zipkin, Jaeger, OTLP)

In Spring Boot 3.x, the stack is:  
`Micrometer Tracing API → micrometer-tracing-bridge-otel → OTel SDK → Zipkin Exporter`

---

### Q2: "What is MDC and how does it help with distributed logging?"

**Answer:**  
MDC (Mapped Diagnostic Context) is a thread-local key-value store in SLF4J/Logback. Any key put into MDC is automatically included in every log line written on that thread — no need to pass values through every method call.

In a distributed system:
1. Request arrives → MDC populated with `traceId`, `userId`, `requestId`
2. All log lines in this request automatically include these fields
3. After request: `MDC.clear()` prevents context leaking to next request

This enables Kibana queries like `traceId: "abc123"` to return ALL log lines from a single distributed request across ALL services.

---

### Q3: "Explain the difference between a Trace, a Span, and a TraceId."

**Answer:**
- **TraceId**: A unique ID assigned to one end-to-end request as it flows through all services. Same across all services.
- **Span**: One unit of work within a service. Has its own `spanId`, start time, end time, and tags. One trace = multiple spans.
- **ParentId**: The spanId of the calling span — creates the parent-child relationship that forms the trace tree.

Example: User places order (one trace):
```
TraceId: abc123
  Span 1: api-gateway            spanId: 001  parentId: null (root)
  Span 2: order-service          spanId: 002  parentId: 001
  Span 3: inventory-service      spanId: 003  parentId: 002
  Span 4: payment-service        spanId: 004  parentId: 002
```

---

### Q4: "Why use both ELK and Zipkin? Isn't that redundant?"

**Answer:**  
Not at all — they serve different purposes:

**ELK**: Log aggregation. Stores every log line with full message text. Enables full-text search, error analysis, audit trails. Answers: *"What exactly happened in this service?"*

**Zipkin**: Distributed tracing. Stores only spans (start/end times, service names). Enables timeline visualization across services. Answers: *"Which service was slow, and what path did this request take?"*

The key is the **traceId link**: you spot a slow span in Zipkin → copy the traceId → filter Kibana by that traceId → read the exact log lines explaining why it was slow.

Without ELK, you know a service was slow but not why.  
Without Zipkin, you know there's an error but not which service in the chain caused it.

---

### Q5: "How do you prevent logging from impacting application performance?"

**Answer:**
1. **Async Appender**: Wraps the Logstash TCP appender in `AsyncAppender`. Log calls put messages in a queue (non-blocking). A background thread handles actual I/O.
2. **`neverBlock: true`**: If the queue fills up, drop the log instead of blocking the request thread.
3. **Appropriate log levels**: Use DEBUG/TRACE only in dev. INFO in production for your own code. WARN for infrastructure.
4. **Avoid expensive log construction**: Use parameterized logging:
   ```java
   // BAD: String concatenation happens BEFORE level check
   log.debug("Order: " + order.toString());  // Expensive even if debug is disabled
   
   // GOOD: Only evaluated if debug is enabled
   log.debug("Order: {}", order);
   ```
5. **Conditional logging with isEnabled checks** for very expensive operations:
   ```java
   if (log.isDebugEnabled()) {
       log.debug("Full state: {}", expensiveSerializationMethod());
   }
   ```

---

### Q6: "What is sampling and when should you use different sampling rates?"

**Answer:**  
Sampling determines what percentage of requests are traced. 100% sampling means every request creates spans sent to Zipkin — in high-volume production, this generates enormous data volumes and adds latency.

| Environment | Rate | Reasoning |
|-------------|------|-----------|
| Development | 100% | Full visibility during development |
| Staging     | 100% | Catch all issues before production |
| Production  | 5-10%| Cost vs coverage tradeoff |
| Critical paths (payments, auth) | 100% | Always trace financial/security operations |

In Spring Boot: `management.tracing.sampling.probability: 0.1`

For adaptive sampling (sample 100% of errors): use a custom `SpanExporter` filter that forces export when status is ERROR regardless of sampling rate.

---

### Q7: "How do you handle log security and compliance?"

**Answer:**
1. **Never log PII** (email in clear text, full credit card numbers, SSNs, passwords)
2. **Mask sensitive data** at the filter level before it reaches Logstash
3. **Index-level access control** in Elasticsearch (RBAC): developers can read, security team can read all, ops can manage
4. **Log retention policies**: Financial logs 7 years (compliance), debug logs 30 days, info logs 90 days
5. **Audit logs**: Separate index `amazondemo-audit-*` for user authentication events, data access events
6. **Encryption**: Encrypt Elasticsearch indices at rest, TLS for Logstash TCP connections in production

---

### Q8: "What is the difference between push-based and pull-based logging?"

**Answer:**

| | Push-based (this project) | Pull-based |
|-|-|-|
| **How** | Service pushes logs to Logstash via TCP | Log aggregator (Filebeat) reads log files |
| **Example** | `LogstashTcpSocketAppender` | Filebeat reads `/var/log/*.log` |
| **Pros** | Real-time, structured JSON, no file I/O | Service doesn't know about logging infra |
| **Cons** | Service depends on Logstash availability | Parsing unstructured text is fragile |
| **Best for** | Containers/microservices, structured logs | VMs with file-based logging |
| **Used in** | This project (logback TCP appender) | Legacy systems, Kubernetes sidecar |

In Kubernetes, a hybrid is common: services write to stdout → Kubernetes log driver captures → Filebeat DaemonSet ships to Logstash.

---

## Architecture Summary Diagram

```
REQUEST LIFECYCLE (with observability):

  Client
    │ POST /api/v1/orders
    ▼
  API Gateway  ──────────────────────────────────────────┐
    │ creates traceId="abc123"                           │
    │ creates spanId="span001"                           │ span logged
    │ injects into MDC                                   │ to Logstash
    │ injects traceparent header into outbound call      │
    ▼                                                    │
  Order Service                                          │
    │ reads traceparent header → continues trace         │ span logged
    │ creates child spanId="span002"                     │ to Logstash
    │ calls Inventory Service                            │
    ▼                                                    │
  Inventory Service                                      │
    │ continues trace with spanId="span003"              │ span logged
    │ performs DB query (40ms)                           │ to Logstash
    │ returns result                                     │
    ▼                                                    │
  Zipkin  ←───── spans collected ───────────────────────┘
    │ Stores: abc123 → [span001 5ms] [span002 450ms] [span003 400ms]
    │
    ▼
  Elasticsearch ←──── JSON log lines ──── Logstash ←──── all services
    │ Index: amazondemo-order-service-2026.05.29
    │ Each log line contains traceId="abc123"
    │
    ▼
  Kibana
    └── KQL: traceId: "abc123"
        └── Shows ALL log lines from ALL services for this one request
```

---

*Next Steps:*
- *[See 05-REDIS-CACHING.md for caching and performance]*
- *[See 12-DOCKER-KUBERNETES.md for container observability with Kubernetes log drivers]*
- *[See 14-TESTING-TUTORIAL.md for testing with mocked Zipkin/Logstash]*
