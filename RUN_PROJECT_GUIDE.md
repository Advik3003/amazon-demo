# How to Run Amazon Demo

## Option 1: Docker Compose (Recommended)

### Prerequisites
- Docker Desktop running
- 8GB RAM available

### Steps
```bash
# 1. Clone/navigate to project
cd amazon-demo

# 2. Start all infrastructure
docker compose up -d postgres mongo redis kafka rabbitmq zookeeper mailhog localstack

# 3. Wait ~30 seconds for infrastructure to start, then start services
docker compose up -d config-server discovery-server

# 4. Wait ~15 seconds for config + discovery, then start services
docker compose up -d auth-service user-service product-service inventory-service order-service payment-service notification-service api-gateway

# 5. Start frontend
docker compose up -d frontend

# 6. Verify everything is running
docker compose ps
```

### Access Points (Docker)
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080
- Eureka Dashboard: http://localhost:8761 (admin/admin123)
- Kafka UI: http://localhost:8090
- RabbitMQ UI: http://localhost:15672 (guest/guest)
- Mailhog: http://localhost:8025

---

## Option 2: Run Locally (Development)

### Prerequisites
- Java 17
- Maven 3.9+
- Node.js 22+
- PostgreSQL 16 running
- MongoDB 7 running
- Redis 7 running
- (Optional) Kafka, RabbitMQ for full features

### Step 1: Start Infrastructure Only
```bash
# Start just the databases
docker compose up -d postgres mongo redis
```

### Step 2: Create Databases
```bash
# Connect to PostgreSQL and create databases
docker exec -it amazon-demo-postgres psql -U postgres -c "
CREATE DATABASE auth_db;
CREATE DATABASE user_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE batch_db;
"
```

### Step 3: Build Backend
```bash
cd backend
mvn install -DskipTests
```

### Step 4: Start Services (in order)

Open 8 terminal windows:

**Terminal 1 - Config Server:**
```bash
cd backend/config-server
mvn spring-boot:run
# Wait for: Started ConfigServerApplication
```

**Terminal 2 - Discovery Server:**
```bash
cd backend/discovery-server
mvn spring-boot:run
# Wait for: Started DiscoveryServerApplication
# Check: http://localhost:8761
```

**Terminal 3 - Auth Service:**
```bash
cd backend/auth-service
mvn spring-boot:run
```

**Terminal 4 - Product Service:**
```bash
cd backend/product-service
mvn spring-boot:run
```

**Terminal 5 - Order Service:**
```bash
cd backend/order-service
mvn spring-boot:run
```

**Terminal 6 - Other Services (same pattern):**
```bash
cd backend/user-service && mvn spring-boot:run
```

**Terminal 7 - API Gateway (start last):**
```bash
cd backend/api-gateway
mvn spring-boot:run
```

**Terminal 8 - Frontend:**
```bash
cd frontend
npm run dev
# Opens: http://localhost:3000
```

---

## Quick API Test

```bash
# 1. Register
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@test.com","password":"Test@1234"}'

# 2. Login
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@test.com","password":"Test@1234"}'

# 3. Get Products (save token from step 2)
TOKEN="your_access_token_here"
curl http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $TOKEN"

# 4. Create Product (as admin)
curl -X POST http://localhost:8080/api/v1/products \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Product","price":29.99,"description":"Great product"}'
```

## Swagger UI

Visit: http://localhost:8080/swagger-ui.html

All service APIs are aggregated in one place.

## Troubleshooting

### Service not starting?
1. Check if port is in use: `netstat -ano | findstr :8081`
2. Check if database is running: `docker compose ps`
3. Check logs: `docker compose logs auth-service`

### "Connection refused" errors?
- Services depend on each other - start in order (Config → Discovery → Services)
- Eureka registration takes ~30 seconds

### Kafka errors?
- Start Kafka: `docker compose up -d zookeeper kafka`
- Services with `fail-fast: false` will start without Kafka but events won't work
