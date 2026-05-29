# CQRS Deep Dive — Tutorial with Code Walkthrough

> **What you'll learn**: Why CQRS exists, how it's implemented in the Product Service
> using PostgreSQL + MongoDB + Kafka, and how to think about it in interviews.

---

## 1. The Problem CQRS Solves

Imagine a `products` table with 10 million rows. You have two very different needs:

```
WRITE (Command):
  - Create product         → validate business rules, update ONE record
  - Update stock           → transactional, needs ACID guarantees
  - Frequency: low

READ (Query):
  - Search by keyword      → full-text search across name/description/brand
  - Filter by category     → range queries + pagination
  - Get featured products  → complex aggregations
  - Frequency: VERY high (100x more than writes)
```

**The conflict:**
- Writes need **relational integrity** (foreign keys, transactions) → PostgreSQL
- Reads need **fast full-text search, flexible queries, no JOINs** → MongoDB
- Optimizing one hurts the other

**CQRS solution:** Use different models, different databases, different code paths.

---

## 2. CQRS Architecture in this Project

```
WRITE SIDE (Command)                    READ SIDE (Query)
─────────────────────                   ─────────────────
ProductCommandService                   ProductQueryService
        │                                       │
        ▼                                       ▼
  PostgreSQL                             MongoDB
  (product_db)                           (product_read_db)
  products table                         products collection
  categories table                       (denormalized, indexed)
        │                                       ▲
        │                                       │
        └──── Kafka: product-events ────────────┘
               (synchronization)
```

### Key principle:
- **One write → one Kafka event → one MongoDB update**
- Read model is **eventually consistent** (lag of milliseconds)
- Read model is **denormalized** (no JOINs needed for queries)

---

## 3. The Write Model — PostgreSQL

### Entities

```java
// Product.java (JPA Entity — the command model)
@Entity
@Table(name = "products")
public class Product {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    private BigDecimal price;
    private String brand;
    private String imageUrl;
    private Boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")    // Foreign key — enforced by PostgreSQL
    private Category category;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

// Category.java
@Entity
@Table(name = "categories")
public class Category {
    @Id
    private String id;
    private String name;
    private String slug;

    @OneToMany(mappedBy = "category")
    private List<Product> products;      // One category → many products
}
```

### Command Service (writes only)

```java
@Service
@RequiredArgsConstructor
@Transactional          // All operations in a transaction
public class ProductCommandService {

    private final ProductRepository productRepository;      // PostgreSQL
    private final CategoryRepository categoryRepository;
    private final ProductEventPublisher eventPublisher;     // Kafka
    private final ProductReadRepository readRepository;     // MongoDB (for cache invalidation)

    public ProductResponse createProduct(ProductRequest request) {

        // 1. Business validation
        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Category not found: " + request.getCategoryId()));

        // 2. Write to PostgreSQL (source of truth)
        Product product = Product.builder()
            .id(UUID.randomUUID().toString())
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .brand(request.getBrand())
            .category(category)
            .build();

        product = productRepository.save(product);  // ACID transaction

        // 3. Publish Kafka event (async — read side will update)
        eventPublisher.publishProductCreated(product);

        // 4. Immediately sync to MongoDB for consistency (optional — Kafka handles it too)
        syncToReadModel(product);

        return mapToResponse(product);
    }

    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        product.setName(request.getName());
        product.setPrice(request.getPrice());
        // ... update fields

        product = productRepository.save(product);

        // Publish UPDATE event → Kafka → MongoDB read model updated
        eventPublisher.publishProductUpdated(product);

        return mapToResponse(product);
    }

    private void syncToReadModel(Product product) {
        ProductReadModel readModel = ProductReadModel.builder()
            .id(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .price(product.getPrice())
            .brand(product.getBrand())
            .categoryId(product.getCategory().getId())
            .categoryName(product.getCategory().getName())  // Denormalized!
            .active(product.getActive())
            .createdAt(product.getCreatedAt())
            .build();

        readRepository.save(readModel);
    }
}
```

---

## 4. The Kafka Event Bridge

### Event Publisher (Write Side → Kafka)

```java
@Service
@RequiredArgsConstructor
public class ProductEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.product-events:product-events}")
    private String productEventsTopic;

    public void publishProductCreated(Product product) {
        ProductEvent event = ProductEvent.builder()
            .eventType("PRODUCT_CREATED")
            .productId(product.getId())
            .name(product.getName())
            .description(product.getDescription())
            .price(product.getPrice())
            .brand(product.getBrand())
            .categoryId(product.getCategory().getId())
            .categoryName(product.getCategory().getName())
            .active(product.getActive())
            .timestamp(LocalDateTime.now())
            .build();

        // Key = productId ensures all events for same product go to same partition
        // (preserves ordering per product)
        kafkaTemplate.send(productEventsTopic, product.getId(), event);
        log.info("Published PRODUCT_CREATED event for: {}", product.getId());
    }

    public void publishProductUpdated(Product product) {
        ProductEvent event = /* ... same but eventType = "PRODUCT_UPDATED" */;
        kafkaTemplate.send(productEventsTopic, product.getId(), event);
    }

    public void publishProductDeleted(String productId) {
        ProductEvent event = ProductEvent.builder()
            .eventType("PRODUCT_DELETED")
            .productId(productId)
            .timestamp(LocalDateTime.now())
            .build();
        kafkaTemplate.send(productEventsTopic, productId, event);
    }
}
```

### Event Consumer (Kafka → Read Side)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final ProductReadRepository readRepository;

    @KafkaListener(
        topics = "${app.kafka.topics.product-events:product-events}",
        groupId = "product-service-read-group"
    )
    public void handleProductEvent(ProductEvent event) {
        log.info("Received event: {} for product: {}", event.getEventType(), event.getProductId());

        switch (event.getEventType()) {
            case "PRODUCT_CREATED" -> createReadModel(event);
            case "PRODUCT_UPDATED" -> updateReadModel(event);
            case "PRODUCT_DELETED" -> readRepository.deleteById(event.getProductId());
        }
    }

    private void createReadModel(ProductEvent event) {
        ProductReadModel readModel = ProductReadModel.builder()
            .id(event.getProductId())
            .name(event.getName())
            .description(event.getDescription())
            .price(event.getPrice())
            .brand(event.getBrand())
            .categoryId(event.getCategoryId())
            .categoryName(event.getCategoryName())  // Denormalized — no JOIN needed
            .active(event.getActive())
            .createdAt(event.getTimestamp())
            .build();

        readRepository.save(readModel);
        log.info("Read model created for product: {}", event.getProductId());
    }

    private void updateReadModel(ProductEvent event) {
        readRepository.findById(event.getProductId()).ifPresent(existing -> {
            existing.setName(event.getName());
            existing.setPrice(event.getPrice());
            // ... update fields
            readRepository.save(existing);
        });
    }
}
```

---

## 5. The Read Model — MongoDB

### MongoDB Document (denormalized)

```java
// ProductReadModel.java — MongoDB document
@Document(collection = "products")
public class ProductReadModel {

    @Id
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private String brand;
    private String imageUrl;
    private Boolean active;

    // DENORMALIZED — category data copied here, no JOIN needed
    private String categoryId;
    private String categoryName;    // ← This would require a JOIN in PostgreSQL
    private String categorySlug;

    // Stock info from inventory service (optionally synced)
    private Integer stockQuantity;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

### MongoDB Repository (reads only)

```java
public interface ProductReadRepository extends MongoRepository<ProductReadModel, String> {

    // MongoDB full-text search (uses text index on name/description/brand)
    @Query("{ $text: { $search: ?0 } }")
    Page<ProductReadModel> searchByText(String searchTerm, Pageable pageable);

    // Query by category (fast because categoryId is indexed)
    Page<ProductReadModel> findByCategoryIdAndActiveTrue(String categoryId, Pageable pageable);

    // Complex filter query
    @Query("{ 'price': { $gte: ?0, $lte: ?1 }, 'categoryId': ?2, 'active': true }")
    Page<ProductReadModel> findByPriceRangeAndCategory(
        BigDecimal minPrice, BigDecimal maxPrice, String categoryId, Pageable pageable);

    List<ProductReadModel> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);
}
```

### Query Service (reads only)

```java
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductReadRepository readRepository;   // MongoDB
    private final RedisTemplate<String, Object> redisTemplate;

    @Cacheable(value = "products", key = "#id")           // Redis cache
    public ProductReadModel getProductById(String id) {
        return readRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
    }

    // Search does NOT use cache (results vary by query)
    public Page<ProductReadModel> searchProducts(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("score").descending());
        return readRepository.searchByText(query, pageable);
    }

    @Cacheable(value = "products-by-category", key = "#categoryId + '-' + #page")
    public Page<ProductReadModel> getProductsByCategory(String categoryId, int page, int size) {
        return readRepository.findByCategoryIdAndActiveTrue(
            categoryId, PageRequest.of(page, size));
    }
}
```

---

## 6. MongoDB Text Index Setup

```java
// MongoConfig.java
@Component
@RequiredArgsConstructor
@Slf4j
public class MongoConfig implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        ensureTextIndex();
    }

    private void ensureTextIndex() {
        try {
            MongoCollection<Document> collection = mongoTemplate.getCollection("products");

            Document indexKeys = new Document("name", "text")
                .append("description", "text")
                .append("brand", "text");

            IndexOptions options = new IndexOptions()
                .name("product_text_search")
                .weights(new Document("name", 2)        // Name matches score 2x
                    .append("description", 1)            // Description 1x
                    .append("brand", 1));                // Brand 1x

            collection.createIndex(indexKeys, options);
            log.info("MongoDB text index ensured on products collection");
        } catch (Exception e) {
            log.warn("Text index may already exist: {}", e.getMessage());
        }
    }
}
```

---

## 7. Eventual Consistency — What it Means

```
Timeline:
  t=0: User creates product via POST /api/v1/products
  t=1: PostgreSQL record created (write model up to date)
  t=2: Kafka event published
  t=3: Kafka consumer processes event
  t=4: MongoDB record created (read model up to date)

Gap t=1 to t=4 (typically 50-200ms):
  If you GET /api/v1/products/{id} at t=1:
  → write model (PostgreSQL) has it ✓
  → read model (MongoDB) doesn't have it yet ✗

This is "eventual consistency"
```

**How we handle this in the API:**
- `POST /products` → returns product from PostgreSQL (always consistent)
- `GET /products` → reads from MongoDB (may be slightly stale)
- `GET /products/{id}` → reads from MongoDB with Redis cache

**For the rare case where strong consistency is needed:**
```java
// Force read from PostgreSQL write model (bypass eventual consistency)
@GetMapping("/{id}/sync")
public ProductResponse getProductDirect(@PathVariable String id) {
    return productCommandService.getProductDirectFromSource(id);
}
```

---

## 8. Complete Data Flow — Create Product

```
POST /api/v1/products
{ "name": "iPhone 15", "price": 999, "categoryId": "cat-001" }

  │
  ▼
[API Gateway]
  Validates JWT → X-User-Id: admin-1, X-User-Roles: ADMIN
  Routes to → lb://product-service
  │
  ▼
[ProductController]
  @PreAuthorize("hasRole('ADMIN')")
  productCommandService.createProduct(request, "admin-1")
  │
  ▼
[ProductCommandService]
  1. categoryRepository.findById("cat-001")   → PostgreSQL SELECT
  2. productRepository.save(product)           → PostgreSQL INSERT
  3. eventPublisher.publishProductCreated()    → Kafka SEND
  4. syncToReadModel()                         → MongoDB INSERT
  Returns ProductResponse
  │
  ▼
[ProductEventPublisher → Kafka]
  topic: product-events
  key: "prod-abc-123"   (ensures ordering)
  value: { eventType: "PRODUCT_CREATED", name: "iPhone 15", ... }
  │
  ▼ (async, ~100ms later)
[ProductEventConsumer]
  @KafkaListener(topic = "product-events")
  handleProductEvent(event)
  → readRepository.save(readModel)   → MongoDB INSERT (redundant but safe)

Final state:
  PostgreSQL: products table has iPhone 15 ✓
  MongoDB:    products collection has iPhone 15 ✓
  Redis:      cache invalidated (next read will refresh) ✓
```

---

## 9. Interview Questions: CQRS

**Q: What is CQRS and when should you use it?**
> Command Query Responsibility Segregation separates read and write operations into
> different models/paths. Use it when read and write patterns have very different
> requirements (different scaling, different query patterns). NOT needed for simple
> CRUD apps — adds significant complexity.

**Q: What is the difference between CQRS and Event Sourcing?**
> - **CQRS**: Separates read and write models. Current state is stored.
> - **Event Sourcing**: Current state is derived from a sequence of events (the event
>   log IS the source of truth). CQRS and Event Sourcing are complementary but different.
> - This project uses CQRS without full Event Sourcing.

**Q: How do you handle the consistency gap in CQRS?**
> Options:
> 1. Accept eventual consistency (most cases — milliseconds lag is fine)
> 2. Return write-model data from create/update endpoints (what we do)
> 3. Wait for confirmation that read model was updated (increases coupling)
> 4. Saga pattern for critical multi-step operations

**Q: What happens if Kafka is down when a product is created?**
> The write (PostgreSQL) succeeds but the Kafka event fails. The read model
> won't be updated. Solutions:
> 1. **Transactional Outbox Pattern**: Write the event to a DB table in the same
>    transaction, then a separate process publishes it to Kafka.
> 2. **Polling-based sync**: A batch job periodically syncs PostgreSQL → MongoDB.
> 3. **Kafka retries**: Set `retries: 3` in KafkaTemplate for transient failures.

**Q: Why use MongoDB for the read model instead of PostgreSQL?**
> MongoDB advantages for reads:
> - Schema-less → easily store denormalized documents
> - Built-in text search (`$text` operator)
> - Fast for reads without JOINs (document = one product with all data)
> - Scales horizontally easier for read-heavy workloads
> - Aggregation pipeline for complex reports
