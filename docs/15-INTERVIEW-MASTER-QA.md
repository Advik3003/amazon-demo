# Master Interview Q&A — All Topics

> **All the key questions and model answers from this project, organized by topic.**
> Use this as your final revision before interviews.

---

## SECTION 1: Microservices Architecture

**Q1: What is a microservice? How is it different from a monolith?**
> A microservice is an independently deployable service that does ONE thing well,
> has its own database, and communicates via HTTP/messaging.
>
> | | Monolith | Microservices |
> |--|---------|--------------|
> | Deployment | One big artifact | Many small services |
> | Scaling | Scale whole app | Scale only bottlenecks |
> | Tech diversity | One stack | Each service can differ |
> | Fault isolation | One failure → all down | Failure isolated |
> | Complexity | Simple to develop | Complex operations |

**Q2: How do microservices communicate?**
> **Synchronous**: HTTP/REST via Feign Client + Eureka load balancing. Good for
> request/response where you need an immediate answer.
>
> **Asynchronous**: Kafka or RabbitMQ. Good for events where you don't need an
> immediate response. The sender continues without waiting.
>
> This project uses both: Feign for inventory checks (synchronous), Kafka for
> order events to notifications (asynchronous).

**Q3: What are the challenges of microservices you've faced?**
> 1. **Distributed transactions**: An order touches order-service, inventory-service, payment-service. If payment fails after inventory is reduced, how do you rollback? → Saga pattern.
> 2. **Service discovery**: Services come and go. → Eureka for dynamic registration.
> 3. **Centralized config**: 10 services × 4 environments = 40 config files. → Config Server.
> 4. **Distributed tracing**: A request spans 5 services. Which one is slow? → Correlation IDs.
> 5. **Eventual consistency**: After a product is created, the MongoDB read model may lag. → Accept it for most cases.

**Q4: Explain the Saga pattern for distributed transactions.**
> A saga breaks a distributed transaction into local transactions. Each step publishes
> an event. If a step fails, compensating transactions undo previous steps.
>
> **Choreography** (event-driven):
> ```
> Order placed → [order-service: create order] → event
>             → [inventory-service: reserve stock] → event
>             → [payment-service: charge card] → event
>             → [order-service: confirm order]
>
> If payment fails:
>             ← [inventory-service: release stock] (compensating transaction)
>             ← [order-service: cancel order]
> ```
>
> **Orchestration** (central coordinator):
> A saga orchestrator tells each service what to do and handles failures.

---

## SECTION 2: Spring Boot & Spring Cloud

**Q5: What is Spring Boot autoconfiguration?**
> Spring Boot scans classpath and automatically configures beans based on what's available.
> If `spring-boot-starter-data-jpa` is on classpath + `spring.datasource.url` is set →
> Spring Boot auto-creates DataSource, EntityManagerFactory, JpaTransactionManager.
> You don't write any configuration — it "just works". Override with your own `@Bean`.

**Q6: What is the difference between @Component, @Service, @Repository, @Controller?**
> All are `@Component` specializations (all create Spring beans). The difference is
> semantic and enables specific behaviors:
> - `@Repository`: Enables exception translation (JPA → Spring DataAccessException)
> - `@Service`: No extra behavior. Just marks "this is business logic"
> - `@Controller`/`@RestController`: Enables request mapping, view resolution
> - `@Component`: Generic. Use when none of the above applies.

**Q7: What is @Transactional and how does Spring implement it?**
> `@Transactional` marks a method to run in a database transaction (begin → commit/rollback).
>
> Spring implements it with **AOP proxies**:
> ```
> Your code calls: orderService.createOrder()
> Spring proxy intercepts it:
>   1. BEGIN TRANSACTION
>   2. Call real orderService.createOrder()
>   3. If success: COMMIT
>   4. If RuntimeException: ROLLBACK
> ```
>
> Key gotcha: `@Transactional` doesn't work on `private` methods or when calling
> a method from within the same class (self-invocation bypasses the proxy).

**Q8: What is Spring AOP? Give an example.**
> Aspect-Oriented Programming allows you to add cross-cutting behavior (logging, security,
> transactions) without modifying the business code.
>
> ```java
> @Aspect
> @Component
> public class PerformanceLoggingAspect {
>
>     @Around("@annotation(LogPerformance)")
>     public Object logTime(ProceedingJoinPoint joinPoint) throws Throwable {
>         long start = System.currentTimeMillis();
>         Object result = joinPoint.proceed();  // Execute the real method
>         long time = System.currentTimeMillis() - start;
>         log.info("{} took {}ms", joinPoint.getSignature(), time);
>         return result;
>     }
> }
> ```
> Spring uses this internally for `@Transactional`, `@Cacheable`, `@Async`.

---

## SECTION 3: Databases

**Q9: What is the difference between @OneToMany(fetch=LAZY) and EAGER loading?**
> - **LAZY** (default): Related entities loaded on demand (separate SQL query when accessed)
> - **EAGER**: Related entities loaded immediately with the parent (JOIN in same query)
>
> Problem with EAGER: Loading 1000 orders loads all their items immediately (N+1 problem).
> Use LAZY and explicitly fetch when needed with JOIN FETCH in JPQL.

**Q10: What is the N+1 query problem? How do you solve it?**
> ```
> // N+1 problem:
> List<Order> orders = orderRepository.findAll();  // 1 query
> for (Order order : orders) {
>     order.getItems();  // N queries (one per order!)
> }
> // 1 + N = 101 queries for 100 orders!
>
> // Solution: JOIN FETCH
> @Query("SELECT o FROM Order o JOIN FETCH o.items WHERE o.userId = :userId")
> List<Order> findOrdersWithItems(String userId);  // 1 query!
> ```

**Q11: When would you use MongoDB over PostgreSQL?**
> Choose MongoDB when:
> - Schema is flexible or evolves frequently
> - Data is naturally document-shaped (nested objects, arrays)
> - You need full-text search natively
> - Horizontal scaling is more important than ACID
>
> In this project: MongoDB for product read model (denormalized, text search, no JOINs).
> PostgreSQL for orders (ACID transactions, foreign keys, financial accuracy).

**Q12: What is Redis and what do you use it for in this project?**
> Redis is an in-memory data store. We use it for:
> 1. **Cache**: Product data cached with TTL (300s). Reduces MongoDB queries.
> 2. **Token blacklist**: Logged-out JWTs stored with TTL = token's remaining lifetime.
> 3. **Session data**: User preferences, cart (if needed).
> 4. **Rate limiting**: API Gateway rate limiter uses Redis counters.
>
> Redis is O(1) for GET/SET. A cache hit takes ~0.1ms vs a MongoDB query ~5-50ms.

---

## SECTION 4: Event-Driven Architecture

**Q13: What is the difference between synchronous and asynchronous communication?**
> **Synchronous**: Caller waits for response. Tight coupling. If downstream is slow → caller is slow.
> Best for: "I need the answer NOW to continue" (check inventory before placing order).
>
> **Asynchronous**: Caller sends message and moves on. Loose coupling. If downstream is slow → caller is unaffected.
> Best for: "I don't need an immediate response" (send confirmation email after order).

**Q14: How do you handle a message that fails to process?**
> 1. **Retry**: Attempt processing 3 times with exponential backoff (1s, 2s, 4s)
> 2. **Dead Letter Queue**: After max retries, move to DLQ
> 3. **Alert**: Notify ops team about DLQ messages
> 4. **Manual review**: Ops investigates and decides to retry or discard
>
> In code (RabbitMQ): `channel.basicNack(deliveryTag, false, false)` → sends to DLQ
> In code (Kafka): `@RetryableTopic(attempts = 3)` → then `@DltHandler`

**Q15: What is Exactly-Once delivery in Kafka?**
> Kafka guarantees **at-least-once** delivery by default (may re-deliver on failure).
> **Exactly-once** is achieved with Kafka transactions:
> - Producer uses idempotent producer (each message has unique ID, duplicates ignored)
> - Transactions group DB write + Kafka publish atomically
> Exactly-once is expensive. Usually, design consumers to be **idempotent** (safe to
> process twice) instead.

---

## SECTION 5: Security

**Q16: Explain the complete authentication flow in this project.**
> 1. User POSTs credentials to `/api/v1/auth/login`
> 2. Auth-service validates password (BCrypt comparison)
> 3. Generates access token (HS512 JWT, 15min) and refresh token (7 days)
> 4. Saves refresh token hash to PostgreSQL
> 5. Returns tokens to client
> 6. Client includes access token in `Authorization: Bearer {token}` header
> 7. API Gateway validates JWT signature and expiry on every request
> 8. Gateway checks Redis blacklist (for logged-out tokens)
> 9. Gateway adds X-User-Id header and forwards to microservice
> 10. Microservice trusts X-User-Id without re-validating JWT

**Q17: What is the difference between OAuth2 and JWT?**
> - **JWT**: A token FORMAT (JSON structure with signature). Not a protocol.
> - **OAuth2**: An AUTHORIZATION PROTOCOL (delegates access to third parties).
>
> They're often used together: OAuth2 flow issues JWTs as access tokens.
> This project uses JWT for auth but doesn't implement full OAuth2 with external providers.
> Adding Google login would be OAuth2 (Google is the authorization server).

**Q18: How do you prevent SQL injection in Spring/JPA?**
> JPA/Hibernate uses parameterized queries by default, preventing SQL injection:
> ```java
> // Safe - parameterized
> @Query("SELECT p FROM Product p WHERE p.name = :name")
> findByName(@Param("name") String name);
>
> // DANGEROUS - string concatenation (never do this)
> @Query("SELECT p FROM Product p WHERE p.name = '" + name + "'")
> findByNameUnsafe(String name);
> ```
> Also: use `@Valid` for input validation, never expose internal entity IDs directly.

---

## SECTION 6: System Design

**Q19: Design a rate limiter for the API Gateway.**
> **Token bucket algorithm** (what we use with Redis):
> ```
> Each user has a bucket with N tokens (e.g., 100 per minute)
> Each request consumes 1 token
> Bucket refills at rate R (e.g., 10/second)
> If bucket empty → 429 Too Many Requests
>
> Redis implementation:
>   GET user:rateLimit:user-1 → current tokens
>   If > 0: DECR and allow
>   If 0: reject
>   EXPIRE user:rateLimit:user-1 60 (reset after 1 minute)
> ```

**Q20: How would you make the product search scalable to millions of products?**
> 1. **Elasticsearch** instead of MongoDB text search (purpose-built for search)
> 2. **Caching**: Cache popular searches (top 1000 queries) in Redis
> 3. **Pagination**: Always return pages, never full results
> 4. **Read replicas**: Scale MongoDB reads horizontally
> 5. **CDN**: Cache product images at edge
> 6. **Background indexing**: Index updates via Kafka events (async)

**Q21: How would you handle the case where Config Server is down?**
> Three levels of resilience:
> 1. `optional:configserver:...` → service starts with local defaults if Config Server unreachable
> 2. Config Server client caches last fetched config locally
> 3. Config Server itself can run multiple instances behind a load balancer
> 4. Git-based config (not native) → Config Server reads from Git, which is always available

---

## SECTION 7: DevOps & Deployment

**Q22: What is a Docker layer and why does layer caching matter?**
> Each Dockerfile instruction creates a layer. Docker caches layers. If an instruction
> hasn't changed (and previous layers are same), Docker reuses the cached layer.
>
> Why it matters for builds:
> ```
> # Layer 1: Base image (rarely changes) → CACHED
> # Layer 2: pom.xml + mvn dependencies (changes infrequently) → CACHED
> # Layer 3: Source code compile (changes every commit) → REBUILD
> # Result: 90% of build skipped, only the changed part rebuilt
> ```

**Q23: What is a Kubernetes Pod? Why don't you deploy containers directly?**
> A Pod is a wrapper around one or more containers that share network and storage.
> Containers in a pod communicate via `localhost`.
>
> Why not containers directly?
> - Pod is the smallest schedulable unit — K8s schedules pods, not containers
> - Pod provides a stable network identity (IP) — containers within share it
> - Sidecar pattern: main app + logging agent + proxy all in one pod

**Q24: Explain zero-downtime deployment in Kubernetes.**
> **Rolling Update**: K8s gradually replaces old pods with new ones.
> ```
> Config: maxSurge=1, maxUnavailable=0 (zero-downtime)
> Step 1: Start 1 new pod (v2) alongside old pods (v1)
> Step 2: Wait for new pod's readiness probe to pass
> Step 3: Terminate 1 old pod (v1)
> Step 4: Repeat until all pods are v2
> ```
> At no point are ALL old pods killed — always at least 1 serving traffic.

---

## SECTION 8: Performance & Optimization

**Q25: How do you identify and fix slow API endpoints?**
> 1. **Structured logging with timing**: Log request duration via filter
> 2. **Actuator metrics**: `/actuator/metrics/http.server.requests` for percentiles
> 3. **Database query analysis**: Enable `EXPLAIN ANALYZE` in PostgreSQL, check slow query log
> 4. **Common causes and fixes**:
>    - N+1 queries → JOIN FETCH
>    - Missing database index → add index on WHERE/JOIN columns
>    - No caching → add `@Cacheable` with Redis
>    - Large payload → pagination, field filtering
>    - Synchronous calls → make async with Kafka

**Q26: What is connection pooling and why is it important?**
> Creating a database connection is expensive (~100ms). Connection pools pre-create
> connections and reuse them.
>
> HikariCP (default in Spring Boot):
> ```yaml
> datasource:
>   hikari:
>     maximum-pool-size: 10    # Max concurrent connections
>     minimum-idle: 5          # Keep 5 connections warm
>     connection-timeout: 30000 # Wait max 30s for connection
> ```
> Without pooling: 1000 requests = 1000 connection creations = very slow.
> With pooling: 1000 requests share 10 connections = fast.

---

## SECTION 9: Quick-Fire Questions

| Question | Answer |
|----------|--------|
| What is idempotency? | Same request multiple times = same result. Important for retries (POST should be idempotent with a key). |
| Difference between `@RequestBody` and `@RequestParam`? | `@RequestBody` reads JSON body. `@RequestParam` reads query string. |
| What is `@SpringBootTest` vs `@WebMvcTest`? | `@SpringBootTest` loads full context. `@WebMvcTest` only web layer (faster). |
| What is `Mono<T>` and `Flux<T>`? | Project Reactor types. `Mono` = 0 or 1 item (async). `Flux` = 0 to N items (stream). Used in reactive/WebFlux code. |
| What is `@Async`? | Marks a method to run in a separate thread pool. Caller continues immediately. Returns `CompletableFuture`. |
| What is a Bean scope? | Singleton (default, one instance), Prototype (new instance each time), Request, Session. |
| Difference between `@PathVariable` and `@RequestParam`? | `/{id}` = PathVariable. `?page=1` = RequestParam. |
| What is `ResponseEntity`? | Wrapper for HTTP response (status code + headers + body). |
| What is CORS? | Cross-Origin Resource Sharing. Browser blocks JS requests to different origins. Configure `@CrossOrigin` or global CORS config to allow. |
| What is Spring Boot Actuator? | Provides production-ready endpoints: `/health`, `/metrics`, `/env`, `/info`. Used for monitoring and K8s probes. |

---

## SECTION 10: Behavioral Questions

**Q27: "Tell me about a complex technical problem you solved."**
> *Use this project:*
> "I implemented CQRS in the product service with separate PostgreSQL (writes) and MongoDB
> (reads) models synchronized via Kafka. The challenge was ensuring the MongoDB text index
> was always present for search queries. I solved it with a programmatic index creation on
> startup using `ApplicationRunner`, and added `spring.data.mongodb.auto-index-creation: true`
> as a safety net. I also handled the eventual consistency gap by having the API return
> data from the write model (PostgreSQL) immediately after create/update."

**Q28: "Why did you use both Kafka and RabbitMQ? Isn't one enough?"**
> "They solve different problems. Kafka is an event log — it retains messages and allows
> multiple consumers to replay them. Perfect for CQRS sync where multiple services need
> the same event. RabbitMQ is a task queue — messages are deleted after consumption,
> supports complex routing (exchanges), and message acknowledgment is simpler. Perfect
> for 'do this task exactly once' scenarios like payment notifications. Using the right
> tool for each job makes the system more reliable."

**Q29: "How did you handle the case where the Config Server might be down?"**
> "I used `spring.config.import: optional:configserver:...`. The `optional:` prefix
> means each microservice has local fallback defaults in its own `application.yml`.
> If Config Server is unavailable at startup, the service starts with these defaults.
> This prevents a single Config Server failure from taking down all services. In production,
> I'd also run Config Server with multiple replicas and Git-based backend for high availability."

**Q30: "What would you improve about this architecture?"**
> (From IMPROVEMENT_SUGGESTIONS.md):
> 1. **Observability**: Add distributed tracing (Jaeger/Zipkin) with OpenTelemetry for end-to-end request tracking across services.
> 2. **Outbox Pattern**: Add transactional outbox to guarantee Kafka events are sent even if the service crashes.
> 3. **RS256 JWT**: Use asymmetric keys — services only need public key, preventing token forgery.
> 4. **Service Mesh**: Istio for mTLS between services, preventing inter-service impersonation.
> 5. **Idempotency Keys**: Add idempotency keys to order creation to prevent duplicate orders on retry.

---

## Study Plan

**Week 1:** Read docs 08-11 (Spring Cloud, CQRS, Kafka, JWT)
**Week 2:** Read docs 12-14 (Docker, Batch, Testing)
**Week 3:** Review all interview questions and practice explaining out loud
**Day before interview:** Review this master Q&A file

**Key concepts to explain confidently:**
- Microservices trade-offs (complexity vs scalability)
- CQRS read/write separation with eventual consistency
- How JWT authentication flows through the gateway
- Kafka consumer groups and partition ordering
- Circuit breaker states (CLOSED → OPEN → HALF-OPEN)
- Docker multi-stage builds and layer caching
- K8s rolling updates for zero-downtime deployments
