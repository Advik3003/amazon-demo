# CQRS - Command Query Responsibility Segregation

## What Is CQRS?

CQRS is a pattern where **reads (queries) and writes (commands) are separated** into different models.

## Why CQRS?

In a traditional app:
- Same database table handles both reads and writes
- Complex queries slow down writes and vice versa
- Can't scale reads and writes independently

With CQRS:
- **Write model** (Command side): Optimized for data integrity
- **Read model** (Query side): Optimized for fast reads

## Implementation in Amazon Demo

### Product Service (CQRS)

```
COMMAND SIDE (Write):
POST /api/v1/products → ProductCommandService → PostgreSQL
                                              ↓
                                        Kafka: PRODUCT_CREATED event

QUERY SIDE (Read):
GET /api/v1/products → ProductQueryService → Redis cache → MongoDB
                                  ↑
                            Kafka consumer (updates MongoDB from events)
```

### Data Flow

1. Admin creates a product: `POST /products`
2. `ProductCommandService` saves to PostgreSQL
3. Publishes `PRODUCT_CREATED` event to Kafka
4. `ProductEventConsumer` (same service) reads from Kafka
5. Consumer updates MongoDB read model
6. User browses products: `GET /products`
7. `ProductQueryService` checks Redis cache first
8. Cache miss → queries MongoDB
9. Stores result in Redis for next request

### Why Two Databases?

| PostgreSQL (Write) | MongoDB (Read) |
|-------------------|----------------|
| ACID compliance | Flexible schema |
| Strong consistency | Denormalized documents |
| Normalized data | No JOINs needed |
| Slower reads | Fast reads |

### Eventual Consistency

⚠️ **Important**: There's a small delay (~milliseconds) between writing to PostgreSQL and the data appearing in MongoDB. This is called **eventual consistency**.

This is acceptable for product reads because:
- Users can wait a fraction of a second for updates to appear
- Read performance is more important than immediate consistency

## Code Example

```java
// COMMAND SIDE - ProductCommandService.java
@Transactional
public ProductResponse createProduct(ProductRequest request, String sellerId) {
    Product product = mapper.toEntity(request);
    Product saved = productRepository.save(product);  // PostgreSQL
    eventPublisher.publishProductCreated(saved);       // Kafka event
    return mapper.toResponse(saved);
}

// QUERY SIDE - ProductQueryService.java
@Cacheable(value = "product", key = "#productId")
public ProductResponse getProductById(String productId) {
    // Redis cache → MongoDB (NO PostgreSQL)
    return productReadRepository.findById(productId)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException(...));
}
```

## CQRS Interview Questions

**Q: What are the drawbacks of CQRS?**
A: Eventual consistency, increased complexity, more infrastructure needed.

**Q: When should you NOT use CQRS?**
A: Simple CRUD apps, small teams, when complexity outweighs benefits.

**Q: How do you handle the read model being out of sync?**
A: Accept eventual consistency (for most reads), or use event replay to rebuild the read model.
