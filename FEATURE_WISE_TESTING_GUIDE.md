# Feature-wise Testing Guide

## Feature 1: Authentication

### Unit Tests (Already Written)
- `AuthServiceTest.java` - Tests login, register, duplicate email, locked account

### Integration Test (Manual)
1. Start `auth-service` with H2 in-memory DB for testing
2. Test complete register → login → refresh → logout flow
3. Verify token is blacklisted after logout

## Feature 2: CQRS (Product Service)

### Verify CQRS is Working
1. Create a product via `POST /api/v1/products`
2. Check PostgreSQL: `SELECT * FROM products WHERE name = 'Test';`
3. Wait 1-2 seconds (Kafka event processing)
4. Check MongoDB: `db.products.findOne({name: "Test"})`
5. Verify data matches

### Verify Redis Cache
1. Install Redis CLI: `docker exec -it amazon-demo-redis redis-cli`
2. `KEYS product:*` - should be empty initially
3. Make a GET product request
4. `KEYS product:*` - should have cached product
5. `TTL product:<id>` - verify TTL is 300 seconds (5 min)

## Feature 3: Event-Driven Orders

### Verify Kafka Events
1. Open Kafka UI: http://localhost:8090
2. Navigate to Topics → `order-events`
3. Place an order via API
4. See the `ORDER_PLACED` event appear in Kafka UI
5. Check Mailhog (http://localhost:8025) for confirmation email

## Feature 4: Circuit Breaker

### Test Circuit Breaker
1. Start order-service
2. Stop inventory-service: `docker compose stop inventory-service`
3. Place an order
4. Verify: Order is created despite inventory-service being down
5. The fallback returns "out of stock" safely
6. Restart inventory-service: `docker compose start inventory-service`
7. Verify: Next order works normally

## Feature 5: Batch Jobs

### Trigger Batch Job Manually
```bash
curl -X POST http://localhost:8088/api/v1/batch/jobs/order-report/run
```
Expected: Job runs and logs output in batch-service logs

## Feature 6: Rate Limiting

### Test Rate Limiting
1. Send 200+ rapid requests to `/api/v1/products`
2. After threshold, should receive 429 Too Many Requests

## Feature 7: Swagger API Docs

### Verify Swagger
1. Open http://localhost:8080/swagger-ui.html
2. All services should appear in dropdown
3. Test an API directly from Swagger:
   - Click "Authorize" → Enter Bearer token
   - Try POST /api/v1/products
   - Verify response

## Feature 8: LocalStack S3

### Test File Upload (LocalStack)
```bash
# Create a test bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket

# Upload a file
aws --endpoint-url=http://localhost:4566 s3 cp test.jpg s3://amazon-demo-uploads/

# List files
aws --endpoint-url=http://localhost:4566 s3 ls s3://amazon-demo-uploads/
```
