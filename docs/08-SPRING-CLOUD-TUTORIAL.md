# Spring Cloud Tutorial — Complete Guide

> **What you'll learn**: Config Server, Service Discovery (Eureka), API Gateway,
> Circuit Breaker (Resilience4j), and how they all work together in this project.

---

## 1. What is Spring Cloud?

Spring Cloud is a collection of libraries that makes it easy to implement common
distributed-systems patterns:

```
Without Spring Cloud:
  Every service manually tracks others' URLs → brittle, hard to scale

With Spring Cloud:
  Services register themselves → others discover them dynamically
  Config lives centrally → change without redeploying code
  Gateway handles cross-cutting concerns → auth, routing, rate limiting
```

**Components used in this project:**

| Component | Library | Port | Role |
|-----------|---------|------|------|
| Config Server | `spring-cloud-config-server` | 8888 | Central config store |
| Discovery Server | `spring-cloud-netflix-eureka-server` | 8761 | Service registry |
| API Gateway | `spring-cloud-gateway` | 8080 | Entry point / router |
| Circuit Breaker | `spring-cloud-circuitbreaker-resilience4j` | - | Fault tolerance |
| Feign Client | `spring-cloud-openfeign` | - | Service-to-service HTTP |

---

## 2. Spring Cloud Config Server

### Why it exists

**Problem:** 10 microservices, each with `application.yml`. When you need to change
a database password, you must update 10 files and redeploy 10 services.

**Solution:** One Config Server holds all configurations. Services fetch their config
at startup via HTTP.

### How it works — Step by Step

```
Service starts
    │
    ▼
bootstrap.yml: "my name is auth-service, get config from http://config-server:8888"
    │
    ▼
Config Server receives: GET /auth-service/local
    │
    ▼
Config Server reads: config/auth-service.yml + config/application-local.yml
    │
    ▼
Returns merged JSON → service uses it as properties
```

### Config Server setup (this project)

```java
// ConfigServerApplication.java
@SpringBootApplication
@EnableConfigServer        // ← This annotation activates everything
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# config-server/application.yml
spring:
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config   # reads from src/main/resources/config/
  profiles:
    active: native                              # "native" = filesystem (not Git)
```

### How clients consume config

```yaml
# Any microservice's application.yml
spring:
  application:
    name: auth-service          # This name is used to find auth-service.yml
  config:
    import: optional:configserver:http://config-server:8888
    # "optional:" = start even if Config Server is unreachable (uses local defaults)
```

### Config file naming convention

Spring Cloud Config resolves properties in **layered order** (highest to lowest):

```
application-{profile}.yml       → global, profile-specific
{service-name}-{profile}.yml    → service-specific, profile-specific
{service-name}.yml              → service-specific base
application.yml                 → global base
```

Example for `auth-service` with profile `stage`:

```
1. application.yml              ← global base (kafka, redis defaults)
2. auth-service.yml             ← auth's base (port, datasource)
3. application-stage.yml        ← stage global (LocalStack endpoints)
4. auth-service-stage.yml       ← auth stage overrides  ← HIGHEST PRIORITY
```

### Testing the Config Server

```bash
# Fetch config for auth-service in local profile
curl http://localhost:8888/auth-service/local

# Fetch config for product-service in stage profile
curl http://localhost:8888/product-service/stage

# Force a service to reload config without restart
curl -X POST http://localhost:8081/actuator/refresh
```

### Interview Questions: Config Server

**Q: What is Spring Cloud Config Server and why use it?**
> A central configuration service that externalizes config from code. All microservices
> fetch properties at startup. Enables: environment-specific config, runtime config
> refresh without redeploy, secret management, auditability.

**Q: What is the difference between `spring.config.import` and `bootstrap.yml`?**
> Spring Boot 2.4+ replaced `bootstrap.yml` with `spring.config.import`. Both achieve
> the same result — loading external config before the application context is created.
> `optional:` prefix means the service starts even if Config Server is down.

**Q: How do you refresh config without restarting a service?**
> 1. Add `@RefreshScope` to beans that should re-read config.
> 2. POST to `/actuator/refresh` on the service.
> 3. For all services at once, use Spring Cloud Bus (publishes a refresh event to Kafka/RabbitMQ).

---

## 3. Eureka Service Discovery

### Why it exists

**Problem:** `auth-service` needs to call `user-service`. But user-service might
run on 3 pods with different IPs. Which IP do you hardcode?

**Solution:** Every service registers itself with Eureka. When auth-service needs
user-service, it asks Eureka: "Where is user-service?" and gets a live IP.

### How it works

```
1. SERVICE REGISTRATION (on startup)
   user-service → tells Eureka: "I'm user-service, I'm at 172.18.0.5:8082"
   Eureka stores it in a registry

2. HEARTBEAT (every 30 seconds)
   user-service → sends heartbeat to Eureka
   If Eureka doesn't get a heartbeat for 90 seconds → removes the instance

3. SERVICE DISCOVERY (on each call)
   auth-service asks: "Give me instances of user-service"
   Eureka returns: ["172.18.0.5:8082", "172.18.0.8:8082"]
   auth-service load-balances between them
```

### Eureka Server setup

```java
@SpringBootApplication
@EnableEurekaServer        // ← Activates Eureka server
public class DiscoveryServerApplication { }
```

```yaml
eureka:
  client:
    register-with-eureka: false   # Server doesn't register itself
    fetch-registry: false
  server:
    enable-self-preservation: false  # Don't protect stale instances in dev
```

### Eureka Client setup (every microservice)

```yaml
eureka:
  client:
    service-url:
      defaultZone: http://admin:admin123@discovery-server:8761/eureka/
    fetch-registry: true        # Download the registry
    register-with-eureka: true  # Register this service
  instance:
    prefer-ip-address: true     # Register with IP, not hostname
    lease-renewal-interval-in-seconds: 10
    lease-expiration-duration-in-seconds: 30
```

### How services call each other (Feign + LoadBalancer)

```java
// In order-service
@FeignClient(name = "inventory-service")   // "inventory-service" is the Eureka name
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{productId}")
    ApiResponse<InventoryResponse> getInventory(@PathVariable String productId);
}

// Spring Cloud LoadBalancer automatically:
// 1. Looks up "inventory-service" in Eureka
// 2. Gets list of instances: ["172.18.0.7:8084"]
// 3. Picks one (round-robin by default)
// 4. Makes the HTTP call to that IP
```

### Interview Questions: Eureka

**Q: What happens if Eureka server goes down?**
> Each service client caches the registry locally. It can still call other services
> using the cached data. After Eureka comes back, clients re-sync. This is why
> `fetch-registry: true` is important — clients have a local copy.

**Q: What is Eureka self-preservation mode?**
> If Eureka doesn't receive heartbeats from >85% of registered instances, it suspects
> a network partition — not that all services died. So it stops evicting instances.
> This prevents mass de-registration during network hiccups. We disable it in dev
> because we frequently start/stop services.

**Q: Client-side vs Server-side load balancing?**
> - **Client-side** (Eureka + Spring LoadBalancer): The calling service fetches all
>   instances from Eureka and picks one itself. No single point of failure.
> - **Server-side** (Nginx, AWS ALB): A separate load balancer picks the instance.
>   Simpler client code but an extra network hop.

---

## 4. API Gateway (Spring Cloud Gateway)

### Why it exists

Without a gateway, clients must know every service's IP and port:
```
Mobile app → http://auth-service:8081/login
Mobile app → http://product-service:8083/products
Mobile app → http://order-service:8085/orders
```

With a gateway, one entry point:
```
Mobile app → http://gateway:8080/api/v1/auth/login
Mobile app → http://gateway:8080/api/v1/products
Mobile app → http://gateway:8080/api/v1/orders
```

### Gateway = Router + Filter Chain

```
Incoming Request
      │
      ▼
[Pre-Filters]                 ← Run before routing
  ├── CORSFilter
  ├── CorrelationIdFilter     ← Adds X-Correlation-ID header
  └── JwtAuthenticationFilter ← Validates JWT, checks Redis blacklist
      │
      ▼
[Route Matching]              ← Which service handles this?
  path: /api/v1/products/** → product-service
  path: /api/v1/orders/**   → order-service
      │
      ▼
[Load Balancing]              ← Which instance of product-service?
  lb://product-service        ← Spring Cloud LoadBalancer resolves from Eureka
      │
      ▼
[Downstream Service]          ← Request forwarded with enriched headers
  product-service:8083
      │
      ▼
[Post-Filters]                ← Run after response
  └── ResponseTimeFilter      ← Logs how long the request took
      │
      ▼
Response returned to client
```

### Route configuration

```yaml
# api-gateway application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: product-service
          uri: lb://product-service     # lb:// = use LoadBalancer (Eureka)
          predicates:
            - Path=/api/v1/products/**  # Match incoming path
            - Path=/api/v1/categories/**
          filters:
            - StripPrefix=0             # Don't strip path prefix
            - name: CircuitBreaker
              args:
                name: product-service
                fallbackUri: forward:/fallback/product

        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**
          # No JWT filter for auth endpoints (public)
```

### JWT Authentication Filter (key code)

```java
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    // Paths that don't need authentication
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/products"          // GET products is public
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip JWT check for public endpoints
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Bearer token
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = parseJwtToken(token);   // Validate signature + expiry

            // Check Redis blacklist (logged-out tokens)
            String blacklistKey = "blacklist:" + token;
            return redisTemplate.hasKey(blacklistKey)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) return unauthorizedResponse(exchange);

                    // Enrich request with user context — downstream services trust these headers
                    ServerHttpRequest enrichedRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Email", claims.get("email", String.class))
                        .header("X-User-Roles", claims.get("roles", String.class))
                        .header("X-Correlation-ID", getOrGenerateCorrelationId(exchange))
                        .build();

                    return chain.filter(exchange.mutate().request(enrichedRequest).build());
                });

        } catch (JwtException e) {
            return unauthorizedResponse(exchange);
        }
    }
}
```

### Rate Limiting

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 10 requests/second
                redis-rate-limiter.burstCapacity: 20    # burst up to 20
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@userKeyResolver}"     # Rate limit per user
```

### Interview Questions: API Gateway

**Q: What is the difference between Gateway and Load Balancer?**
> A load balancer distributes traffic to multiple instances of the **same** service.
> A gateway routes traffic to **different** services and handles cross-cutting concerns
> (auth, logging, rate limiting). Spring Cloud Gateway does both — it routes + load-balances.

**Q: What is the difference between a pre-filter and post-filter?**
> - **Pre-filter**: Runs before the request is forwarded (auth check, add headers, log request)
> - **Post-filter**: Runs after the downstream response arrives (modify response, log timing)
> `GlobalFilter` runs for all routes. `GatewayFilter` runs for specific routes.

**Q: How does the gateway know which service to call?**
> Route predicates match the incoming request (by path, header, method, etc.).
> Then `lb://service-name` triggers Spring Cloud LoadBalancer, which asks Eureka
> for instances of `service-name` and picks one using round-robin.

---

## 5. Circuit Breaker (Resilience4j)

### The Problem: Cascading Failures

```
order-service calls inventory-service
inventory-service is slow (database overloaded)
order-service threads pile up waiting
order-service runs out of threads
API Gateway times out on order-service
ALL requests fail — even unrelated ones
```

### The Circuit Breaker Pattern

Like an electrical circuit breaker — opens when it detects a fault, preventing damage.

```
CLOSED state (normal operation)
  ├── Track success/failure rate
  └── If failure rate > 50% for 10 calls → OPEN

OPEN state (blocking calls)
  ├── All calls immediately return fallback
  ├── Don't even try the failing service
  └── After 30 seconds → HALF-OPEN

HALF-OPEN state (testing recovery)
  ├── Allow 3 trial calls
  ├── If 2+ succeed → CLOSED (service recovered)
  └── If 2+ fail → OPEN again
```

### Resilience4j Configuration

```yaml
resilience4j:
  circuitbreaker:
    instances:
      inventory-service:
        registerHealthIndicator: true
        slidingWindowSize: 10           # Evaluate last 10 calls
        minimumNumberOfCalls: 5         # Need at least 5 calls to evaluate
        failureRateThreshold: 50        # Open if 50%+ fail
        waitDurationInOpenState: 30s    # Stay open for 30 seconds
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true

  retry:
    instances:
      inventory-service:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - feign.FeignException
          - java.net.ConnectException

  timelimiter:
    instances:
      inventory-service:
        timeoutDuration: 5s             # Fail fast after 5 seconds
```

### Using Circuit Breaker in code

```java
// In order-service
@Service
@RequiredArgsConstructor
public class OrderService {

    private final InventoryClient inventoryClient;

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "checkInventoryFallback")
    @Retry(name = "inventory-service")
    @TimeLimiter(name = "inventory-service")
    public CompletableFuture<InventoryResponse> checkInventory(String productId) {
        return CompletableFuture.supplyAsync(() ->
            inventoryClient.getInventory(productId).getData()
        );
    }

    // Fallback when circuit is OPEN or all retries fail
    public CompletableFuture<InventoryResponse> checkInventoryFallback(
            String productId, Throwable ex) {
        log.warn("Inventory service unavailable for product: {}. Using fallback.", productId);
        // Return a safe default — assume limited stock available
        return CompletableFuture.completedFuture(
            InventoryResponse.builder()
                .productId(productId)
                .availableQuantity(0)
                .status("UNKNOWN")
                .fallback(true)
                .build()
        );
    }
}
```

### Resilience4j Metrics

```bash
# Check circuit breaker state
curl http://localhost:8085/actuator/circuitbreakers

# Response shows:
{
  "circuitBreakers": {
    "inventory-service": {
      "failureRate": "20.0%",
      "state": "CLOSED",
      "bufferedCalls": 10,
      "failedCalls": 2
    }
  }
}
```

### Interview Questions: Circuit Breaker

**Q: What is the difference between Circuit Breaker, Retry, and Bulkhead?**
> - **Circuit Breaker**: Opens to stop calls to a failing service entirely (prevent cascade)
> - **Retry**: Automatically retries failed calls (for transient faults)
> - **Bulkhead**: Limits concurrent calls to a service (isolate thread pools)
> - **Rate Limiter**: Limits calls per second (prevent overload)
> Together they form the "resilience stack" — usually applied in that order.

**Q: When should you NOT retry?**
> Don't retry non-idempotent operations (e.g., creating an order, charging a card).
> Retrying could create duplicate orders or double charges. Retry only idempotent
> operations (reads, updates with idempotency keys).

**Q: What is the difference between a timeout and a circuit breaker?**
> Timeout: "Give up on THIS call after X seconds"
> Circuit Breaker: "Stop trying THAT service entirely for X seconds because it's failing"
> A circuit breaker uses timeout failures as one of the signals to open.

---

## 6. How it All Connects — Request Flow

```
Browser: GET /api/v1/products?category=electronics&page=0

1. API Gateway (8080)
   ├── CorrelationIdFilter: X-Correlation-ID=abc-123
   ├── JwtAuthenticationFilter: token valid, X-User-Id=user-1
   ├── Route match: /api/v1/products → lb://product-service
   └── LoadBalancer: Eureka says product-service is at 172.18.0.6:8083

2. product-service (8083)
   ├── Receives X-User-Id, X-Correlation-ID in headers
   ├── Controller: searchProducts(category, page)
   ├── ProductQueryService.search()
   ├── Checks Redis cache → MISS → queries MongoDB
   ├── MongoDB full-text search (read model)
   ├── Caches result in Redis (TTL: 60s)
   └── Returns ProductResponse[]

3. API Gateway
   └── Returns response to browser

Key: product-service doesn't know its own IP.
     product-service doesn't validate JWT (gateway already did).
     product-service trusts X-User-Id header from gateway.
```
