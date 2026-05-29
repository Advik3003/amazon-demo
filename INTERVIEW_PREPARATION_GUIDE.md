# Interview Preparation Guide

## Microservices Architecture Questions

### Q: Why did you use microservices for this project?
A: Microservices allow **independent scaling**, **independent deployment**, and **technology flexibility** per service. For example, the Product service can scale independently during high traffic while Payment service uses fewer resources. Each service can be deployed by a different team without affecting others.

**Key benefits demonstrated in this project:**
- Auth service can be secured differently from other services
- Product service scales for read-heavy traffic using CQRS
- Notification service can fail without taking down the entire system

### Q: What are the disadvantages of microservices?
A: 
1. **Distributed systems complexity** - Network calls fail, latency adds up
2. **Data consistency** - No single ACID transaction across services
3. **Operational overhead** - Need Docker, K8s, monitoring, distributed tracing
4. **Testing complexity** - Integration tests are harder

**When NOT to use**: Small teams, MVP phase, monolith is working fine.

---

## CQRS Questions

### Q: Explain CQRS and how you implemented it?
A: CQRS (Command Query Responsibility Segregation) separates reads from writes.

**In Product Service:**
- **Write side**: Admin creates/updates products → saved to PostgreSQL → Kafka event published
- **Read side**: Users browse products → from MongoDB (optimized for reads) → Redis cache on top
- **Sync**: Kafka consumer updates MongoDB when product events arrive

**Why?**: Product listings are read 1000x more than written. MongoDB with denormalized documents serves these faster than PostgreSQL with JOINs.

### Q: How do you handle eventual consistency in CQRS?
A: Accept a small delay (~milliseconds) between write (PostgreSQL) and read model update (MongoDB). For e-commerce product listings, this is acceptable. For order status, we check the command-side database directly.

---

## Event-Driven Architecture Questions

### Q: Why Kafka AND RabbitMQ? Why not just one?
A: Different tools for different needs:

**Kafka** (for streaming):
- Order events need to reach MULTIPLE consumers (inventory, notification, analytics)
- Events must be replayable (if notification service was down, it replays missed events)
- High throughput needed

**RabbitMQ** (for task queues):
- Payment processing needs exactly-once delivery
- Dead-letter queues for failed payments
- Priority queues for VIP customers
- Simpler routing logic

### Q: What happens if Kafka goes down?
A: Services continue working for non-event features. Undelivered events can be handled via:
1. **Outbox Pattern** - Save events to DB first, send to Kafka separately
2. **Retry with backoff** - Producer retries
3. **Graceful degradation** - Inventory updates may lag, but orders still get created

---

## JWT & Security Questions

### Q: How does JWT work in your system?
A:
1. User logs in → auth-service validates credentials
2. auth-service generates JWT (signed with secret key)
3. Client stores JWT (localStorage for web, SharedPreferences for Android)
4. Client sends JWT in `Authorization: Bearer <token>` header
5. **API Gateway** validates JWT signature (no DB call needed)
6. If valid, Gateway adds user info as headers (`X-User-Id`, `X-User-Roles`)
7. Backend services trust these headers (no JWT re-validation)

### Q: How do you handle token expiry and refresh?
A: Two-token strategy:
- **Access Token**: 15 minutes, used for API calls
- **Refresh Token**: 7 days, stored in DB, used ONLY to get new access token

On 401: Client uses refresh token to get new access token. Refresh token is rotated (old one revoked, new one issued). If refresh token is stolen and used, next legitimate refresh fails (token already revoked).

### Q: How do you implement logout properly?
A: JWT can't be "deleted" since it's stateless. Solution:
1. Add access token to Redis blacklist with TTL = remaining token lifetime
2. API Gateway checks blacklist on every request
3. Revoke refresh token in DB

---

## Spring Cloud Questions

### Q: What is Service Discovery and why is it needed?
A: In a microservices deployment, services can have dynamic IPs (Docker container IPs change on restart). Service Discovery (Eureka) allows:
- Services register with Eureka on startup with their hostname + port
- API Gateway asks Eureka for `auth-service` instances
- Eureka returns live instances, enabling load balancing

Without Eureka: hardcoded IPs that break when containers restart.

### Q: Explain Circuit Breaker pattern?
A: Prevents cascade failures. If Order Service calls Inventory Service and inventory is slow/down:

**Without Circuit Breaker**: Order Service threads pile up waiting, eventually Order Service also goes down.

**With Circuit Breaker** (Resilience4j):
1. **Closed**: Normal operation
2. **Open**: After X failures, stop calling service, return fallback immediately
3. **Half-Open**: After timeout, try a few requests to see if service recovered

In our system: If Inventory Service is down, orders can still be created with "pending inventory check" status.

---

## Database Questions

### Q: Why PostgreSQL for writes and MongoDB for reads?
A: 
- **PostgreSQL** (ACID compliant): Orders, payments, user data must be consistent. Can't lose a payment record.
- **MongoDB** (flexible schema): Product listings with varying attributes (electronics vs clothing have different fields). Fast reads with denormalized documents.

### Q: What is Redis used for?
A: 
1. **Product cache**: Cache product listings for 5 minutes. Reduces MongoDB queries by 90%+.
2. **Token blacklist**: When user logs out, store their token hash in Redis with TTL.
3. **Session data**: User preferences, cart items (transient data).

---

## Docker & Kubernetes Questions

### Q: Why Docker?
A: **"Works on my machine" problem**. Docker packages the app with all dependencies. Everyone gets the exact same environment.

### Q: What's the difference between Docker and Kubernetes?
A: 
- **Docker**: Builds and runs containers
- **Kubernetes**: Manages MANY containers - auto-scaling, self-healing, load balancing, rolling deployments

### Q: What are Kubernetes readiness vs liveness probes?
A:
- **Liveness**: "Is the container alive?" → If fails, restart the container
- **Readiness**: "Is the container ready to serve traffic?" → If fails, remove from load balancer but don't restart

In our services: `/actuator/health/liveness` and `/actuator/health/readiness`.

---

## Batch Processing Questions

### Q: When would you use Spring Batch?
A: For processing large amounts of data in chunks:
- Generating daily order reports (1M+ orders → process 1000 at a time)
- Syncing inventory with supplier systems
- Cleanup old notification records
- Email campaigns to 100k users

### Q: What is chunk processing?
A: Instead of loading all 1M records at once (OutOfMemory risk), process in chunks of 1000:
```
READ 1000 records → PROCESS each → WRITE results
READ next 1000 → PROCESS → WRITE
...repeat until done
```

Benefit: If processing fails at record 50,000, only re-process from the last completed chunk.

---

## Architecture Patterns You Can Talk About

1. **12-Factor App** - Stateless, config via env vars, logs to stdout
2. **CQRS** - Separate read/write models
3. **Event Sourcing** - Events as source of truth (partial implementation)
4. **Circuit Breaker** - Resilience4j in API Gateway
5. **Saga Pattern** - Distributed transactions (not implemented, good to mention)
6. **Strangler Pattern** - Migrating monolith to microservices
7. **Outbox Pattern** - Reliable event publishing

---

## Common Interview Questions & Short Answers

| Question | Answer |
|----------|--------|
| Monolith vs Microservices | Monolith: simpler, faster to start. Microservices: scalable, independent deployments, complex |
| How to handle distributed transactions | Saga pattern (choreography or orchestration), eventual consistency |
| API Gateway responsibility | Auth, CORS, rate limiting, routing, circuit breaking, logging |
| How to test microservices | Unit tests (Mockito), integration tests (TestContainers), contract tests (Pact) |
| How to handle service-to-service auth | JWT forwarded by gateway, or service accounts with internal tokens |
| Log aggregation in microservices | ELK Stack (Elasticsearch + Logstash + Kibana), correlation IDs link requests |
| How to do zero-downtime deployments | Kubernetes rolling updates, blue-green deployment, canary releases |
