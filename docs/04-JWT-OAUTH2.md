# JWT Authentication & OAuth2

## JWT (JSON Web Token)

### Structure
```
HEADER.PAYLOAD.SIGNATURE

Example:
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEyMyIsImVtYWlsIjoiam9obkBleGFtcGxlLmNvbSIsInJvbGVzIjoiUk9MRV9VU0VSIiwiaWF0IjoxNzA2MTY2MDAwfQ.abc123
```

### Why JWT?
- **Stateless**: No session storage needed on server
- **Scalable**: Any server can validate (no shared session store)
- **Self-contained**: Token contains user info (no DB lookup per request)

### Security: Two-Token Strategy

```
Access Token  → Short-lived (15 min) → Used for API calls
                                        Validated at API Gateway
                                        NOT stored in DB

Refresh Token → Long-lived (7 days) → Used to get new access token
                                       Stored in DB for revocation
                                       Rotated on each use
```

### Token Validation Flow
```
1. Client sends: GET /api/products
                 Authorization: Bearer eyJhbG...

2. API Gateway receives request
3. Gateway extracts token from Authorization header
4. Gateway validates signature using secret key
5. If valid: extract userId, email, roles
6. Add as headers: X-User-Id, X-User-Email, X-User-Roles
7. Forward to Product Service
8. Product Service trusts these headers
```

### Token Blacklisting (Logout)
```
PROBLEM: JWT is valid until expiry. How to invalidate on logout?

SOLUTION: Store invalidated tokens in Redis until expiry
Key: "blacklist:{token}"
TTL: Same as token expiry

On logout:
  1. Add access token to Redis blacklist
  2. Revoke refresh token in DB
  3. API Gateway checks blacklist on every request
```

## OAuth2 (Industry Standard)

Our implementation uses OAuth2 concepts:
- **Resource Owner**: The user
- **Client**: Frontend app
- **Authorization Server**: auth-service
- **Resource Server**: Other microservices

### Grant Types Used
- **Password Grant**: Username/password → tokens (for mobile/SPA)
- **Refresh Token Grant**: Refresh token → new access token

## Security Best Practices

| Practice | Implementation |
|----------|---------------|
| Short access token expiry | 15 minutes (configurable) |
| Refresh token rotation | New refresh token on each use |
| Token blacklisting | Redis with matching TTL |
| BCrypt password hashing | Strength 12 |
| Account lockout | After 5 failed attempts |
| HTTPS only in production | TLS termination at load balancer |

## Interview Questions

**Q: Why use JWT over sessions?**
A: Sessions require server-side storage and sticky sessions in clusters. JWT is stateless - any server can validate it.

**Q: How do you invalidate a JWT before expiry?**
A: Token blacklisting in Redis. Store the token hash until its expiry time.

**Q: What's in the JWT payload?**
A: userId, email, roles, issuedAt, expiry. NEVER put sensitive data like passwords in JWT!

**Q: Explain refresh token rotation.**
A: When a refresh token is used, it's immediately revoked and a new one is issued. If stolen and used, the original user's next refresh will fail (token already revoked), alerting them to compromise.
