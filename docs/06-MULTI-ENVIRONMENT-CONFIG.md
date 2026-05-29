# Multi-Environment Configuration Guide

## Overview

The Amazon Demo project supports **4 environments**, each with appropriate configurations for databases, messaging, and AWS services:

| Environment | Profile       | AWS Services    | Database  | Purpose                  |
|-------------|---------------|-----------------|-----------|--------------------------|
| **local**   | `local`       | Disabled        | Docker    | Daily development        |
| **test**    | `test`        | Mocked/Stubbed  | H2 In-Memory | Automated CI tests    |
| **stage**   | `stage`       | LocalStack      | Docker    | Pre-production testing   |
| **prod**    | `prod`        | Simulated/Real  | Docker/RDS| Production deployment    |

---

## Architecture

```
environments/
├── .env.local          # LOCAL: all services localhost
├── .env.test           # TEST:  H2 in-memory, no Eureka
├── .env.stage          # STAGE: LocalStack AWS simulation
└── .env.prod           # PROD:  Simulated dummy AWS config

docker-compose.yml          # Base (used for local)
docker-compose.stage.yml    # Staging override (adds LocalStack)
docker-compose.prod.yml     # Production override (restart:always)

backend/config-server/src/main/resources/config/
├── application.yml                    # Global shared config
├── application-local.yml              # LOCAL overrides
├── application-test.yml               # TEST overrides
├── application-stage.yml              # STAGE overrides (LocalStack)
├── application-prod.yml               # PROD overrides (dummy AWS)
├── auth-service.yml                   # Auth base config
├── auth-service-local.yml             # Auth LOCAL
├── auth-service-test.yml              # Auth TEST
├── auth-service-stage.yml             # Auth STAGING
├── auth-service-prod.yml              # Auth PRODUCTION
├── (same pattern for all services)
```

---

## How to Start Each Environment

### LOCAL Environment

```bash
# Linux/Mac
./scripts/start-local.sh

# Windows PowerShell
.\scripts\start-local.ps1

# Manual (docker compose)
docker compose --env-file environments/.env.local up -d
```

**What starts:**
- All infrastructure (Postgres, MongoDB, Redis, Kafka, RabbitMQ)
- LocalStack (S3/SQS available but AWS features disabled)
- Mailhog (catch-all email)
- All Spring Boot microservices with `SPRING_PROFILES_ACTIVE=local`

### TEST Environment

```bash
# Run all tests
cd backend
mvn test -Dspring.profiles.active=test

# Or set globally
export SPRING_PROFILES_ACTIVE=test
mvn test
```

**What this uses:**
- H2 in-memory databases (no external Docker needed)
- Embedded Kafka (`@EmbeddedKafka`)
- Eureka disabled
- Mock AWS services
- Fast token expiry (1 minute)

### STAGING Environment (LocalStack)

```bash
# Linux/Mac
./scripts/start-stage.sh

# Windows PowerShell
.\scripts\start-stage.ps1

# Manual
docker compose -f docker-compose.stage.yml \
    --env-file environments/.env.stage up -d
```

**What's different from LOCAL:**
- `SPRING_PROFILES_ACTIVE=stage`
- **LocalStack** provides real AWS API:
  - S3: `http://localstack:4566`
  - SQS: queues auto-created
  - SNS: topics auto-created
  - SES: identity verification simulated
  - Secrets Manager: secrets pre-populated
- Stronger passwords
- `ddl-auto: validate` (not `update`)
- LocalStack init script runs automatically on startup

**Check LocalStack resources:**
```bash
./scripts/check-localstack.sh

# Or manually:
aws --endpoint-url=http://localhost:4566 s3 ls
aws --endpoint-url=http://localhost:4566 sqs list-queues
aws --endpoint-url=http://localhost:4566 sns list-topics
aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets
```

### PRODUCTION Environment (Simulated)

```bash
# Linux/Mac
./scripts/start-prod.sh

# Windows PowerShell
# (copy start-stage.ps1 pattern with prod files)

# Manual
docker compose -f docker-compose.yml -f docker-compose.prod.yml \
    --env-file environments/.env.prod up -d
```

**Important notes:**
- Uses dummy/placeholder AWS credentials (demo project)
- `ddl-auto: validate` enforced
- All services have `restart: always`
- JSON structured logging
- For **real production**, replace all `DUMMY_*` values in `.env.prod`

---

## Config Server Profile Resolution

Spring Cloud Config Server resolves configs in this order (highest priority first):

1. `application-{profile}.yml` (e.g., `application-stage.yml`)
2. `{service-name}-{profile}.yml` (e.g., `auth-service-stage.yml`)
3. `{service-name}.yml` (e.g., `auth-service.yml`)
4. `application.yml` (global base)

Example: For `auth-service` with profile `stage`, it loads:
1. `application.yml` ← global defaults
2. `auth-service.yml` ← auth service defaults
3. `application-stage.yml` ← stage-specific kafka/redis/etc
4. `auth-service-stage.yml` ← auth-service stage overrides (highest priority)

---

## AWS Configuration Per Environment

### LOCAL & TEST
```yaml
app:
  aws:
    enabled: false
  features:
    s3-uploads: false
    email-notifications: false
    sqs-events: false
```
AWS SDK beans (`S3Client`, `SesClient`) are **not created** (via `@ConditionalOnProperty`).

### STAGE (LocalStack)
```yaml
app:
  aws:
    enabled: true
    endpoint: "http://localstack:4566"
    access-key: "test"       # LocalStack accepts any value
    secret-key: "test"
    s3:
      bucket-name: amazon-demo-stage
      path-style-access: true  # Required for LocalStack
  features:
    s3-uploads: true
    email-notifications: true   # SES via LocalStack
    sqs-events: true            # SQS via LocalStack
```

### PROD (Dummy/Simulated for Demo)
```yaml
app:
  aws:
    enabled: true
    access-key: ${AWS_ACCESS_KEY_ID:AKIADUMMYPRODACCESSKEY}
    secret-key: ${AWS_SECRET_ACCESS_KEY:dummy...}
    s3:
      bucket-name: amazon-demo-prod
      # No endpoint = real AWS
  features:
    s3-uploads: true
    email-notifications: true
    sqs-events: true
```

---

## LocalStack Initialization

When the LocalStack container starts, it automatically runs:
```
infrastructure/localstack/init-localstack.sh
```

This creates:
- **S3 buckets**: `amazon-demo-stage`, `amazon-demo-stage-reports`, `amazon-demo-stage-static`
- **SQS queues**: `order-notifications`, `payment-notifications`, `inventory-alerts` + DLQs
- **SNS topics**: `order-events`, `product-events`, `payment-events`, `inventory-events`
- **SES identities**: `noreply@amazondemo-stage.com` (verified)
- **Secrets Manager**: `jwt-secret`, `db-credentials`, `payment-api-key`
- **SSM Parameters**: `app.env`, `app.version`, `kafka.bootstrap-servers`
- **IAM Policy**: `AmazonDemoStagePolicy`

---

## JWT Secrets Per Environment

| Environment | Secret Value | Expiry |
|-------------|-------------|--------|
| local | `local-dev-jwt-secret-...` | 15min access / 7d refresh |
| test  | `test-env-jwt-secret-...`  | 1min access / 5min refresh |
| stage | `stage-env-jwt-secret-...` | 15min access / 7d refresh |
| prod  | `${JWT_SECRET}` (env var)  | 15min access / 1d refresh |

---

## Database Strategy Per Environment

| Service       | LOCAL          | TEST            | STAGE           | PROD            |
|---------------|---------------|-----------------|-----------------|-----------------|
| auth          | PostgreSQL     | H2 in-memory    | PostgreSQL      | PostgreSQL/RDS  |
| user          | PostgreSQL     | H2 in-memory    | PostgreSQL      | PostgreSQL/RDS  |
| product (W)   | PostgreSQL     | H2 in-memory    | PostgreSQL      | PostgreSQL/RDS  |
| product (R)   | MongoDB        | MongoDB TC      | MongoDB         | MongoDB/Atlas   |
| inventory     | PostgreSQL     | H2 in-memory    | PostgreSQL      | PostgreSQL/RDS  |
| order (W)     | PostgreSQL     | H2 in-memory    | PostgreSQL      | PostgreSQL/RDS  |
| order (R)     | MongoDB        | MongoDB TC      | MongoDB         | MongoDB/Atlas   |
| notification  | MongoDB        | MongoDB TC      | MongoDB         | MongoDB/Atlas   |
| cache/session | Redis          | Redis TC        | Redis           | ElastiCache     |

*(TC = TestContainers)*

---

## Feature Flags

All environments use feature flags to control optional integrations:

```yaml
app:
  features:
    email-notifications: true/false  # SMTP or SES
    sms-notifications: true/false    # SNS SMS
    s3-uploads: true/false           # S3 file storage
    sqs-events: true/false           # SQS event processing
    sns-alerts: true/false           # SNS topic publishing
```

Services check these flags before invoking AWS clients:
- `@ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")`
- `@ConditionalOnProperty(name = "app.features.s3-uploads", havingValue = "true")`

---

## Environment Variable Precedence

Spring Boot resolves properties in this order (highest priority wins):

1. OS environment variables (`SPRING_DATASOURCE_URL`)
2. Docker Compose `environment:` block
3. Config Server (fetched at startup)
4. `application-{profile}.yml`
5. `application.yml`

The Docker Compose files explicitly set `SPRING_PROFILES_ACTIVE` for each service.

---

## Troubleshooting

### Config Server not reachable
Services use `spring.config.import: optional:configserver:http://localhost:8888`  
The `optional:` prefix means services start even if config server is down (uses local defaults).

### LocalStack services not ready
Check LocalStack health:
```bash
curl http://localhost:4566/_localstack/health
docker logs amazon-demo-localstack
```

### AWS credentials error in stage
LocalStack accepts any non-empty credentials. Ensure:
```bash
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test
```

### H2 dialect error in test
Add to test config: `spring.jpa.database-platform: org.hibernate.dialect.H2Dialect`

### Redis authentication error in stage
The stage environment uses Redis with a password. Ensure:
```bash
SPRING_DATA_REDIS_PASSWORD=redis_stage_pass
```
