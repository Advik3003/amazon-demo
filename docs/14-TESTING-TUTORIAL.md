# Testing Tutorial — JUnit 5, Mockito & TestContainers

> **What you'll learn**: Writing effective unit tests, mocking with Mockito,
> integration tests with TestContainers, and testing Spring Boot applications.

---

## 1. Testing Pyramid

```
          /\
         /  \
        / E2E\       ← End-to-end (slow, expensive, few)
       /──────\         Full stack: UI → API → DB
      /  Integr \    ← Integration tests (medium speed, real DBs via TestContainers)
     /────────────\     Test service + DB, test REST endpoints
    /  Unit Tests  \  ← Unit tests (fast, isolated, many)
   /────────────────\    Test one class with mocked dependencies

70% Unit | 20% Integration | 10% E2E
```

---

## 2. JUnit 5 Fundamentals

### Test lifecycle annotations

```java
@ExtendWith(MockitoExtension.class)  // Enable Mockito in JUnit 5
class ProductCommandServiceTest {

    @BeforeAll
    static void setUpOnce() {
        // Runs ONCE before all tests in class
        // Good for: expensive setup (DB connections, server starts)
    }

    @BeforeEach
    void setUp() {
        // Runs before EACH test method
        // Good for: reset state, create fresh test objects
    }

    @AfterEach
    void tearDown() {
        // Runs after EACH test method
        // Good for: cleanup, verify no unexpected interactions
    }

    @AfterAll
    static void tearDownOnce() {
        // Runs ONCE after all tests
        // Good for: close expensive resources
    }

    @Test
    void shouldCreateProduct() { ... }

    @Test
    @DisplayName("Creating product with non-existent category throws exception")
    void createProductWithInvalidCategory() { ... }

    @Test
    @Disabled("Not implemented yet")
    void futureTest() { ... }
}
```

### Assertions

```java
import static org.assertj.core.api.Assertions.*;
// AssertJ gives fluent, readable assertions (preferred over JUnit's assert methods)

// Equality
assertThat(product.getName()).isEqualTo("iPhone 15");
assertThat(product.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));

// Collections
assertThat(products).hasSize(3);
assertThat(products).isNotEmpty();
assertThat(products).extracting("name").contains("iPhone 15", "Samsung S24");

// Null checks
assertThat(product.getId()).isNotNull();
assertThat(product.getDeletedAt()).isNull();

// Exception testing
assertThatThrownBy(() -> service.createProduct(null))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("request cannot be null");

// Or with assertThrows (JUnit 5 style)
ResourceNotFoundException ex = assertThrows(
    ResourceNotFoundException.class,
    () -> service.createProduct(requestWithBadCategory)
);
assertThat(ex.getMessage()).contains("Category not found");
```

### Parameterized Tests

```java
@ParameterizedTest
@ValueSource(strings = {"", "  ", "\t", "\n"})
void shouldRejectBlankProductName(String name) {
    ProductRequest request = new ProductRequest();
    request.setName(name);
    request.setPrice(BigDecimal.TEN);

    assertThatThrownBy(() -> service.createProduct(request))
        .isInstanceOf(IllegalArgumentException.class);
}

@ParameterizedTest
@MethodSource("provideInvalidPrices")
void shouldRejectInvalidPrices(BigDecimal price, String expectedMessage) {
    ProductRequest request = validRequest();
    request.setPrice(price);

    assertThatThrownBy(() -> service.createProduct(request))
        .hasMessageContaining(expectedMessage);
}

static Stream<Arguments> provideInvalidPrices() {
    return Stream.of(
        Arguments.of(BigDecimal.ZERO, "Price must be positive"),
        Arguments.of(new BigDecimal("-1"), "Price must be positive"),
        Arguments.of(null, "Price is required")
    );
}
```

---

## 3. Mockito — Mocking Dependencies

### Basic mocking

```java
@ExtendWith(MockitoExtension.class)
class ProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;        // Mock = fake implementation

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductEventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandService productCommandService;  // Real class, mocks injected

    // Test: successful product creation
    @Test
    void shouldCreateProductSuccessfully() {
        // ARRANGE — set up test data and mock behavior
        Category category = Category.builder()
            .id("cat-001")
            .name("Electronics")
            .build();

        ProductRequest request = ProductRequest.builder()
            .name("iPhone 15")
            .price(new BigDecimal("999.99"))
            .categoryId("cat-001")
            .build();

        Product savedProduct = Product.builder()
            .id("prod-001")
            .name("iPhone 15")
            .price(new BigDecimal("999.99"))
            .category(category)
            .build();

        // Mock behavior: when categoryRepository.findById("cat-001") is called → return category
        when(categoryRepository.findById("cat-001"))
            .thenReturn(Optional.of(category));

        // Mock behavior: when productRepository.save(any product) → return savedProduct
        when(productRepository.save(any(Product.class)))
            .thenReturn(savedProduct);

        // ACT — call the method under test
        ProductResponse response = productCommandService.createProduct(request);

        // ASSERT — verify the result
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("iPhone 15");
        assertThat(response.getId()).isEqualTo("prod-001");

        // VERIFY — check that mocks were called correctly
        verify(categoryRepository).findById("cat-001");             // Called exactly once
        verify(productRepository).save(any(Product.class));          // Called exactly once
        verify(eventPublisher).publishProductCreated(savedProduct);  // Event was published
    }

    // Test: category not found
    @Test
    void shouldThrowExceptionWhenCategoryNotFound() {
        // ARRANGE
        ProductRequest request = ProductRequest.builder()
            .name("iPhone 15")
            .categoryId("non-existent-category")
            .build();

        when(categoryRepository.findById("non-existent-category"))
            .thenReturn(Optional.empty());  // Category doesn't exist

        // ACT + ASSERT
        assertThatThrownBy(() -> productCommandService.createProduct(request))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Category not found");

        // VERIFY — product should NOT be saved if category doesn't exist
        verify(productRepository, never()).save(any());
        verify(eventPublisher, never()).publishProductCreated(any());
    }
}
```

### Common Mockito patterns

```java
// Return different values on subsequent calls
when(service.getCounter())
    .thenReturn(1)
    .thenReturn(2)
    .thenReturn(3);

// Throw exception
when(repository.save(any()))
    .thenThrow(new DataIntegrityViolationException("Duplicate key"));

// Capture arguments passed to mocks
ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
verify(productRepository).save(productCaptor.capture());
Product savedProduct = productCaptor.getValue();
assertThat(savedProduct.getName()).isEqualTo("iPhone 15");
assertThat(savedProduct.getActive()).isTrue();  // Verify default values

// Verify interaction count
verify(eventPublisher, times(1)).publishProductCreated(any());
verify(repository, never()).delete(any());
verify(cache, atLeastOnce()).put(anyString(), any());

// Mock void methods
doNothing().when(emailService).sendEmail(any());  // void method that does nothing
doThrow(new RuntimeException("SMTP down")).when(emailService).sendEmail(any());

// Spy — real object with some methods mocked
@Spy
private JwtService jwtService = new JwtService();  // Real JwtService
// Mock only one method
doReturn("fake-token").when(jwtService).generateAccessToken(any());
```

---

## 4. Spring Boot Test — Web Layer Testing

### Controller test with MockMvc

```java
@WebMvcTest(ProductController.class)    // Only loads web layer (no DB, no services)
@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;            // Virtual HTTP client

    @MockBean
    private ProductQueryService productQueryService;   // Mock service layer

    @MockBean
    private ProductCommandService productCommandService;

    @Autowired
    private ObjectMapper objectMapper;  // For JSON serialization

    @Test
    @WithMockUser(roles = "ADMIN")      // Simulate authenticated admin
    void shouldCreateProduct() throws Exception {
        // ARRANGE
        ProductRequest request = ProductRequest.builder()
            .name("iPhone 15")
            .price(new BigDecimal("999.99"))
            .categoryId("cat-001")
            .description("Latest iPhone")
            .build();

        ProductResponse response = ProductResponse.builder()
            .id("prod-001")
            .name("iPhone 15")
            .price(new BigDecimal("999.99"))
            .build();

        when(productCommandService.createProduct(any())).thenReturn(response);

        // ACT + ASSERT
        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value("prod-001"))
        .andExpect(jsonPath("$.data.name").value("iPhone 15"))
        .andExpect(jsonPath("$.data.price").value(999.99));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}")
        )
        .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")       // User, not admin
    void shouldReturn403WhenUserTriesCreateProduct() throws Exception {
        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\", \"price\":100, \"categoryId\":\"cat-1\"}")
        )
        .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void shouldReturn400WhenProductNameBlank() throws Exception {
        String invalidJson = "{\"name\":\"\", \"price\":100}";

        mockMvc.perform(
            post("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0]").value(containsString("name")));
    }
}
```

---

## 5. TestContainers — Real Database Integration Tests

### Why TestContainers?

```
H2 in-memory:  Fast but not the same as PostgreSQL (different SQL dialect)
PostgreSQL:    Real behavior but needs installed DB (not portable)
TestContainers: Spins up real PostgreSQL in Docker just for the test
                → Real behavior + portable + no manual setup
```

### TestContainers setup

```java
@SpringBootTest
@Testcontainers                        // Enables TestContainers annotations
@ActiveProfiles("test")
@Transactional                         // Rollback after each test
class ProductCommandServiceIntegrationTest {

    @Container
    @ServiceConnection                 // Spring Boot 3.1+ — auto-configures connection
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo =
        new MongoDBContainer("mongo:7.0");

    @Container
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private ProductCommandService productCommandService;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void shouldPersistProductToDatabase() {
        // No mocks — real PostgreSQL running in Docker!
        ProductRequest request = ProductRequest.builder()
            .name("MacBook Pro")
            .price(new BigDecimal("1999.00"))
            .categoryId("cat-001")
            .build();

        // Pre-condition: insert category
        categoryRepository.save(Category.builder().id("cat-001").name("Electronics").build());

        // ACT
        ProductResponse response = productCommandService.createProduct(request);

        // ASSERT — verify actual DB state
        Optional<Product> saved = productRepository.findById(response.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getName()).isEqualTo("MacBook Pro");
        assertThat(saved.get().getCreatedAt()).isNotNull();
    }
}
```

### Kafka Integration Test

```java
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {"product-events", "order-events"})
class ProductEventPublisherTest {

    @Autowired
    private ProductEventPublisher eventPublisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            embeddedKafka.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("product-events"));
    }

    @Test
    void shouldPublishProductCreatedEvent() throws Exception {
        Product product = Product.builder()
            .id("prod-test-001")
            .name("Test Product")
            .price(BigDecimal.TEN)
            .build();

        eventPublisher.publishProductCreated(product);

        // Poll Kafka for the message
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5));
        assertThat(records).hasSize(1);

        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.key()).isEqualTo("prod-test-001");
        assertThat(record.value()).contains("PRODUCT_CREATED");
        assertThat(record.value()).contains("Test Product");
    }
}
```

---

## 6. Test Coverage and Best Practices

### What to test

```
✓ Happy path — normal successful flow
✓ Edge cases — empty lists, null values, max/min values
✓ Error cases — invalid input, resource not found, DB failure
✓ Security — unauthorized access, insufficient role
✓ Business rules — can't order more than stock, can't update closed order
✗ Don't test getters/setters (generated by Lombok)
✗ Don't test trivial constructors
✗ Don't test framework code (Spring's @Transactional itself)
```

### Test naming convention

```java
// Pattern: should{Expected}When{Condition}
void shouldCreateProductSuccessfully()
void shouldThrowExceptionWhenCategoryNotFound()
void shouldReturn401WhenNotAuthenticated()
void shouldFilterOutInactiveProducts()

// Or: given{Condition}_when{Action}_then{Expected}
void givenValidRequest_whenCreateProduct_thenReturnCreatedProduct()
```

### Testing Redis cache behavior

```java
@Test
void shouldReturnCachedProductOnSecondCall() {
    String productId = "prod-001";

    // First call — should hit MongoDB
    ProductReadModel result1 = productQueryService.getProductById(productId);

    // Second call — should hit Redis cache (no MongoDB call)
    ProductReadModel result2 = productQueryService.getProductById(productId);

    assertThat(result1).isEqualTo(result2);

    // Verify MongoDB called only once (second call from cache)
    verify(productReadRepository, times(1)).findById(productId);
}
```

---

## 7. Interview Questions: Testing

**Q: What is the difference between @Mock and @MockBean?**
> - `@Mock` (Mockito): Creates a mock object. Used in unit tests without Spring context.
> - `@MockBean` (Spring): Creates a mock AND registers it as a Spring Bean, replacing any existing bean. Used in `@SpringBootTest` or `@WebMvcTest`.

**Q: What is the difference between unit tests and integration tests?**
> - **Unit test**: Tests ONE class in isolation. All dependencies are mocked. Fast.
> - **Integration test**: Tests multiple components working together. Uses real DB
>   (TestContainers), real Kafka, etc. Slower but tests real behavior.

**Q: What is test slicing in Spring Boot?**
> Instead of loading the full application context, load only what's needed:
> - `@WebMvcTest` — only web layer (controllers, filters)
> - `@DataJpaTest` — only JPA layer (repositories)
> - `@DataMongoTest` — only MongoDB repositories
> - `@JsonTest` — only JSON serialization
> This is faster and more focused than `@SpringBootTest`.

**Q: Why use TestContainers instead of H2?**
> H2 doesn't support all PostgreSQL features (e.g., `jsonb`, some SQL functions,
> PostgreSQL-specific indexes). TestContainers runs the actual PostgreSQL in Docker,
> giving you full compatibility. Tests catch DB-specific bugs that H2 would miss.

**Q: How do you test asynchronous code?**
> ```java
> // Use Awaitility for async assertions
> await().atMost(5, SECONDS)
>     .untilAsserted(() ->
>         assertThat(notificationRepository.findByUserId(userId)).hasSize(1)
>     );
> ```
> For Kafka consumers: use `CountDownLatch` or `@EmbeddedKafka` with polling.
