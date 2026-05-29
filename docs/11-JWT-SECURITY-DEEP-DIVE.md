# JWT & Spring Security Deep Dive — Tutorial

> **What you'll learn**: JWT structure, how Spring Security works, how auth flows
> through the microservices, token blacklisting, and all key interview questions.

---

## 1. JWT — JSON Web Token

### Structure

A JWT has 3 parts separated by dots: `header.payload.signature`

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyLTEiLCJlbWFpbCI6ImpvaG5AZXhhbXBsZS5jb20ifQ.abc123

Part 1 (Header) — Base64 encoded:
  {
    "alg": "HS512",      ← Signing algorithm
    "typ": "JWT"
  }

Part 2 (Payload/Claims) — Base64 encoded:
  {
    "sub": "user-1",                ← Subject (user ID)
    "email": "john@example.com",
    "roles": "ROLE_USER",
    "iat": 1716988800,              ← Issued at (epoch seconds)
    "exp": 1716989700               ← Expiry (15 minutes later)
  }

Part 3 (Signature):
  HMACSHA512(
    base64(header) + "." + base64(payload),
    secret_key
  )
```

**The signature makes JWT tamper-proof.**
If someone modifies the payload (changes role to ADMIN), the signature becomes invalid.

### Token Types in this project

```
ACCESS TOKEN
  Expiry:  15 minutes
  Storage: Memory (not localStorage) — less XSS risk
  Used in: Authorization: Bearer {token}
  Contains: userId, email, roles

REFRESH TOKEN
  Expiry:  7 days (1 day in prod)
  Storage: HttpOnly cookie
  Used in: POST /api/v1/auth/refresh
  Contains: userId only (minimal claims)
  Stored in: PostgreSQL (for revocation)
```

---

## 2. Authentication Flow — Step by Step

### Login Flow

```
1. POST /api/v1/auth/login { email, password }
   │
   ▼
2. API Gateway
   → Path is PUBLIC (/api/v1/auth/**) → skip JWT filter
   → Forward to auth-service:8081
   │
   ▼
3. AuthService.login(email, password)
   → Load user from PostgreSQL by email
   → BCrypt.matches(inputPassword, storedHash)
   → If mismatch → throw UnauthorizedException
   │
   ▼
4. JwtService.generateAccessToken(user)
   → Creates JWT with userId, email, roles
   → Signs with HS512 using JWT_SECRET
   → Expiry: now + 15 minutes
   │
   ▼
5. JwtService.generateRefreshToken(user)
   → Creates JWT with just userId
   → Expiry: now + 7 days
   → Saves refresh token hash to PostgreSQL
   │
   ▼
6. Response:
   {
     "accessToken": "eyJ...",
     "refreshToken": "eyJ...",
     "expiresIn": 900,          ← 900 seconds = 15 minutes
     "user": { id, email, roles }
   }
```

### Authenticated Request Flow

```
1. Client: GET /api/v1/orders
   Authorization: Bearer eyJhbGci...
   │
   ▼
2. API Gateway JwtAuthenticationFilter
   → Extract token from Authorization header
   → Validate JWT signature (using JWT_SECRET)
   → Check token not expired
   → Check Redis: "blacklist:{token}" → not found → token is valid
   → Extract claims: userId, email, roles
   → Add headers to forwarded request:
       X-User-Id: user-1
       X-User-Email: john@example.com
       X-User-Roles: ROLE_USER
   │
   ▼
3. order-service receives request
   → Reads X-User-Id from header (added by gateway)
   → Trusts this header — gateway already validated JWT
   → Returns only this user's orders
```

### Token Refresh Flow

```
1. POST /api/v1/auth/refresh
   Body: { "refreshToken": "eyJ..." }
   │
   ▼
2. AuthService.refreshToken(refreshToken)
   → Validate refresh token signature
   → Check refresh token not expired
   → Look up refresh token in PostgreSQL (verify it wasn't revoked)
   → If found → generate new access token
   │
   ▼
3. Response:
   {
     "accessToken": "eyJ...(new)...",
     "expiresIn": 900
   }
   (refreshToken stays the same until it expires)
```

### Logout Flow

```
1. POST /api/v1/auth/logout
   Authorization: Bearer eyJhbGci...
   │
   ▼
2. AuthService.logout(userId, token)
   → Extract expiry from JWT claims
   → Calculate remaining TTL = expiry - now
   → Add to Redis: SET "blacklist:{token}" "1" EX {remainingTTL}
   → Delete refresh token from PostgreSQL
   │
   ▼
3. Next request with the same token:
   → API Gateway checks Redis: "blacklist:{token}" → FOUND
   → Returns 401 Unauthorized
   → Token is effectively invalidated even before its natural expiry
```

---

## 3. JWT Service — Code Walkthrough

```java
@Service
@Slf4j
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-token-expiry:900000}")  // 15 minutes
    private long accessTokenExpiry;

    @Value("${app.jwt.refresh-token-expiry:604800000}")  // 7 days
    private long refreshTokenExpiry;

    // Generate access token with full user claims
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("roles", user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.joining(",")));
        claims.put("type", "ACCESS");

        return buildToken(claims, user.getId(), accessTokenExpiry);
    }

    // Refresh token has minimal claims (just userId)
    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "REFRESH");
        return buildToken(claims, user.getId(), refreshTokenExpiry);
    }

    private String buildToken(Map<String, Object> extraClaims, String subject, long expiration) {
        return Jwts.builder()
            .claims(extraClaims)
            .subject(subject)                   // userId
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey(), Jwts.SIG.HS512)  // HMAC-SHA512
            .compact();
    }

    // Extract all claims from token (validates signature automatically)
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
        // Throws ExpiredJwtException, SignatureException, MalformedJwtException
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);  // throws if invalid/expired
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
```

---

## 4. Spring Security Configuration

### Auth Service Security Config

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            // Disable CSRF (REST API — no browser sessions)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless session (JWTs don't need server sessions)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/auth/register").permitAll()
                .requestMatchers("/api/v1/auth/refresh").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )

            // Add JWT filter BEFORE UsernamePasswordAuthenticationFilter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // Cost factor 12
        // BCrypt automatically salts and hashes
        // Never store plain text passwords!
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Auth Service JWT Filter

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws Exception {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);  // No token → skip
            return;
        }

        String token = authHeader.substring(7);

        // Check blacklist first (fast Redis lookup)
        if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
            return;
        }

        try {
            String userId = jwtService.extractUserId(token);

            // Only set auth if not already authenticated
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                if (jwtService.isTokenValid(token)) {
                    UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,                           // credentials (null for JWT)
                            userDetails.getAuthorities()    // roles
                        );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Set in SecurityContext → Spring Security knows user is authenticated
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
```

---

## 5. Role-Based Access Control (RBAC)

### Roles in this project

```
ROLE_USER:   Can browse, order, review products
ROLE_ADMIN:  Can create/edit/delete products, view all orders, manage users
ROLE_SELLER: (Future) Can manage their own products
```

### Method-level security

```java
// Enable @PreAuthorize annotations globally
@EnableMethodSecurity(prePostEnabled = true)

// In controllers:
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    // Public — no auth needed
    @GetMapping
    public PageResponse<ProductResponse> getProducts(@RequestParam ...) { }

    // Any authenticated user
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ProductResponse getProduct(@PathVariable String id) { }

    // Only ADMIN can create products
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> createProduct(@RequestBody @Valid ProductRequest request) { }

    // Only ADMIN or the user who created the review
    @DeleteMapping("/{id}/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN') or @reviewService.isOwner(#reviewId, authentication.name)")
    public void deleteReview(...) { }
}
```

### Accessing current user in services

```java
// In auth-service — user is from Spring Security context
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String userId = auth.getName();  // = subject from JWT

// In other microservices — user is from X-User-Id header (set by gateway)
@GetMapping("/my-orders")
public List<OrderResponse> getMyOrders(
        @RequestHeader("X-User-Id") String userId,         // injected by gateway
        @RequestHeader("X-User-Roles") String roles) {
    return orderService.getOrdersByUser(userId);
}
```

---

## 6. Password Security

### BCrypt Hashing

```java
// During registration:
String hashedPassword = passwordEncoder.encode(rawPassword);
// BCrypt generates a random salt and combines with password
// "password123" → "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"

// The hash contains: $2a$ + cost + salt + hash
// Cost 12 = 2^12 = 4096 iterations (computationally expensive)

// During login:
boolean matches = passwordEncoder.matches(rawPassword, storedHash);
// BCrypt extracts salt from stored hash and re-hashes → compares
// Never decode/decrypt — BCrypt is one-way
```

### Why BCrypt is secure

```
1. One-way: Cannot reverse hash to get password
2. Salted: Each hash uses random salt → same password → different hash
   "password" → "$2a$12$abc..." (user 1)
   "password" → "$2a$12$xyz..." (user 2)
   → Rainbow table attacks impossible
3. Slow: Cost factor makes brute-force infeasible
   Cost 12 = ~200ms per hash on modern hardware
   1 billion attempts × 200ms = 200 million seconds → not feasible
```

---

## 7. Security in Microservices — Key Patterns

### Pattern 1: Gateway validates JWT, services trust headers

```
Client ──JWT──► API Gateway ──► Validates JWT ──► Adds X-User-* headers ──► Microservice
```

Services NEVER validate JWT themselves (except auth-service which issues them).
Services trust `X-User-Id` and `X-User-Roles` headers.

⚠️ **Security note:** Services must only accept these headers FROM the gateway, not from external clients. In Kubernetes, use network policies. In Docker, the service ports are not exposed externally.

### Pattern 2: Token Blacklisting with Redis

```java
// Logout: add token to Redis blacklist
long ttl = (expiry.getTime() - System.currentTimeMillis()) / 1000;
redisTemplate.opsForValue().set("blacklist:" + token, "1", ttl, TimeUnit.SECONDS);

// Every request: check Redis before processing
Boolean isBlacklisted = redisTemplate.hasKey("blacklist:" + token);
// Redis O(1) lookup — very fast even with millions of tokens
```

### Pattern 3: Refresh Token Rotation

```
First login:     access_token_1 + refresh_token_1
Token expires:   POST /refresh with refresh_token_1
                 → new access_token_2 + new refresh_token_2
                 → refresh_token_1 invalidated in DB
If stolen refresh token used: → detected (already used) → revoke all sessions
```

---

## 8. Interview Questions: JWT & Security

**Q: What are the advantages of JWT over session-based auth?**
> - **Stateless**: No server-side storage needed — scales horizontally
> - **Self-contained**: All user info in the token — no DB lookup per request
> - **Cross-domain**: Works across microservices and domains
> - **Standard**: RFC 7519 — works with any language/framework
> Disadvantages: Cannot invalidate until expiry (solved with blacklisting), larger than session IDs.

**Q: How do you invalidate a JWT before expiry?**
> Short of invalidation, options:
> 1. **Token blacklist** (what we use): Store revoked tokens in Redis with TTL
> 2. **Version number**: Store a "token version" in DB; include in JWT; reject if version doesn't match
> 3. **Short expiry**: Use 5-minute access tokens (forced re-auth often)
> 4. **Refresh token revocation**: Revoking refresh token prevents getting new access tokens

**Q: What is the difference between authentication and authorization?**
> - **Authentication**: "Who are you?" — verifying identity (username + password → JWT)
> - **Authorization**: "What can you do?" — verifying permissions (ADMIN can create products)
> JWT handles both: it authenticates (valid signature) and carries roles (authorizes).

**Q: Why store refresh tokens in a database?**
> Refresh tokens are long-lived (7 days). If stolen, an attacker has long access.
> Storing in DB allows:
> - Revocation at logout (delete from DB)
> - "Logout all devices" (delete all user's tokens)
> - Rotation detection (if a token is used twice, revoke all)

**Q: What is CSRF and why is it not a concern for JWT/REST APIs?**
> CSRF tricks a logged-in user's browser to make unintended requests.
> It works because browsers automatically send cookies.
> REST APIs with JWT in Authorization header are NOT vulnerable because:
> - JWT is not in cookies (by default) — browser won't auto-send it
> - Even if JWT is in a cookie, use `SameSite=Strict` flag

**Q: What is the difference between HS256 and RS256 for JWT?**
> - **HS256/HS512** (HMAC): Single secret key for both signing and verification.
>   Fast, simpler, but the secret must be shared with all validators.
> - **RS256** (RSA): Private key signs, public key verifies.
>   Better for microservices — services only need the public key (can't forge tokens).
>   We use HS512 in this project (simpler for learning). Production should use RS256.
