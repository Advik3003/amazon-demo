# Redis - Caching Strategy

## What Is Redis Used For?

1. **Product Cache**: Cache product listings for fast reads
2. **Token Blacklist**: Store invalidated JWT tokens
3. **Session Data**: User session information
4. **Rate Limiting**: API rate limit counters

## Cache TTL Strategy

| Cache Key | TTL | Reason |
|-----------|-----|--------|
| `product:{id}` | 5 minutes | Products don't change often |
| `products:page:*` | 1 minute | Listings change with new products |
| `categories` | 1 hour | Categories rarely change |
| `featured` | 10 minutes | Featured products rotate |
| `blacklist:{token}` | Token expiry | Auto-cleanup |

## @Cacheable Annotation

```java
@Cacheable(value = "product", key = "#productId")
public ProductResponse getProductById(String productId) {
    // First call: hits MongoDB, stores in Redis
    // Subsequent calls: returns from Redis (no MongoDB)
    return productReadRepository.findById(productId)...;
}
```

## Cache Invalidation

```java
@CacheEvict(value = {"product", "products"}, key = "#productId")
public ProductResponse updateProduct(String productId, ProductRequest request) {
    // When product is updated, remove it from cache
    // Next GET will fetch fresh data from MongoDB
}
```

## Cache Miss vs Hit

```
First request (CACHE MISS):
Client → ProductQueryService → Redis (MISS) → MongoDB → Store in Redis → Return

Subsequent requests (CACHE HIT):
Client → ProductQueryService → Redis (HIT) → Return immediately
```

## Interview Questions

**Q: What is cache invalidation and why is it hard?**
A: Cache invalidation means removing stale data from cache. It's hard because you need to know when to invalidate and which keys to clear.

**Q: What is cache stampede?**
A: When cache expires and many requests hit the DB simultaneously. Solution: Use probabilistic early recomputation or distributed locking.

**Q: Redis vs Memcached?**
A: Redis supports more data structures (lists, sets, sorted sets), persistence, replication. Memcached is simpler but limited to key-value strings.
