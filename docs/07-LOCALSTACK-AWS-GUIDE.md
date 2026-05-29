# LocalStack AWS Simulation Guide

## What is LocalStack?

LocalStack is an open-source tool that simulates AWS cloud services locally in Docker.  
It provides the **exact same AWS API surface** as real AWS, making it ideal for:

- **Staging environment** testing (without AWS costs)
- **Integration testing** with real AWS SDK calls
- **Developer learning** — work with AWS APIs locally

**LocalStack endpoint**: `http://localhost:4566` (all services unified)

---

## AWS Services Used in This Project

| Service         | Stage Use                             | Prod Use (Real AWS)     |
|-----------------|---------------------------------------|-------------------------|
| **S3**          | Product images, batch reports         | S3 bucket (same API)    |
| **SQS**         | Order/payment notification queues     | SQS queues              |
| **SNS**         | Event fan-out (order→notifications)   | SNS topics              |
| **SES**         | Transactional email sending           | SES with domain verify  |
| **Secrets Manager** | JWT secret, DB credentials      | Secrets Manager         |
| **SSM**         | App configuration parameters          | Parameter Store         |
| **IAM**         | Policy testing (simulated)            | IAM roles               |

---

## Resources Created at Startup

The `infrastructure/localstack/init-localstack.sh` script runs automatically when LocalStack starts.

### S3 Buckets
```
amazon-demo-stage           ← Main uploads bucket
├── product-images/         ← Product image uploads
└── user-avatars/           ← User profile pictures

amazon-demo-stage-reports   ← Batch job output
├── sales-reports/
└── inventory-reports/

amazon-demo-stage-static    ← Static assets
```

### SQS Queues
```
order-notifications          ← Order status updates
payment-notifications        ← Payment results
inventory-alerts             ← Low stock alerts
order-events                 ← Order lifecycle events
order-notifications-dlq      ← Dead letter queue
payment-notifications-dlq    ← Dead letter queue
```

### SNS Topics
```
order-events        → subscribed by: order-notifications SQS queue
product-events
payment-events
inventory-events
user-notifications
```

### SES Email Identities
```
noreply@amazondemo-stage.com    (verified sender)
support@amazondemo-stage.com    (verified sender)
amazondemo-stage.com            (verified domain)
```

### Secrets Manager
```
amazon-demo/stage/jwt-secret          ← JWT signing key
amazon-demo/stage/db-credentials      ← Database user/pass
amazon-demo/stage/rabbitmq-credentials
amazon-demo/stage/payment-api-key
amazon-demo/stage/app-config
```

---

## Interacting with LocalStack

You need the AWS CLI installed. Any credentials work with LocalStack.

```bash
# Set fake credentials (LocalStack accepts anything)
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

# Shorthand alias
alias awslocal='aws --endpoint-url=http://localhost:4566'
```

### S3 Operations
```bash
# List buckets
awslocal s3 ls

# Upload a file
awslocal s3 cp myfile.jpg s3://amazon-demo-stage/product-images/test/img.jpg

# Download a file
awslocal s3 cp s3://amazon-demo-stage/product-images/test/img.jpg ./downloaded.jpg

# List contents
awslocal s3 ls s3://amazon-demo-stage/product-images/ --recursive

# Generate presigned URL (valid for 5 minutes)
awslocal s3 presign s3://amazon-demo-stage/product-images/test/img.jpg --expires-in 300
```

### SQS Operations
```bash
# List queues
awslocal sqs list-queues

# Send a test message
awslocal sqs send-message \
    --queue-url http://localhost:4566/000000000000/order-notifications \
    --message-body '{"orderId":"test-123","status":"CONFIRMED","userId":"user-1"}'

# Receive messages
awslocal sqs receive-message \
    --queue-url http://localhost:4566/000000000000/order-notifications \
    --max-number-of-messages 5

# Get queue attributes (depth)
awslocal sqs get-queue-attributes \
    --queue-url http://localhost:4566/000000000000/order-notifications \
    --attribute-names ApproximateNumberOfMessages
```

### SNS Operations
```bash
# List topics
awslocal sns list-topics

# Publish a message to a topic
awslocal sns publish \
    --topic-arn arn:aws:sns:us-east-1:000000000000:order-events \
    --message '{"eventType":"ORDER_PLACED","orderId":"order-123"}' \
    --subject "New Order Placed"

# List subscriptions
awslocal sns list-subscriptions
```

### SES Operations
```bash
# List verified identities
awslocal ses list-identities

# Send a test email (goes nowhere in LocalStack, but API is validated)
awslocal ses send-email \
    --from noreply@amazondemo-stage.com \
    --destination '{"ToAddresses":["test@example.com"]}' \
    --message '{"Subject":{"Data":"Test"},"Body":{"Text":{"Data":"Hello from LocalStack SES"}}}'
```

### Secrets Manager Operations
```bash
# List secrets
awslocal secretsmanager list-secrets

# Get a secret value
awslocal secretsmanager get-secret-value \
    --secret-id amazon-demo/stage/jwt-secret

# Get DB credentials
awslocal secretsmanager get-secret-value \
    --secret-id amazon-demo/stage/db-credentials \
    --query SecretString --output text | python3 -m json.tool
```

### SSM Parameter Store
```bash
# List parameters
awslocal ssm describe-parameters

# Get a parameter
awslocal ssm get-parameter \
    --name /amazon-demo/stage/app.env

# Put a new parameter
awslocal ssm put-parameter \
    --name /amazon-demo/stage/feature-flag \
    --value "enabled" \
    --type String
```

---

## Spring Boot Integration

### Product Service (S3 Uploads)

The `S3StorageService` bean is only created when `app.aws.enabled=true`:

```java
@Service
@ConditionalOnProperty(name = "app.aws.enabled", havingValue = "true")
public class S3StorageService {
    // Uploads product images to S3 (LocalStack in stage, real S3 in prod)
    public String uploadProductImage(MultipartFile file, String productId) { ... }
    public String generatePresignedUrl(String s3Key, int expirySeconds) { ... }
}
```

### Notification Service (SES Emails)

The `SesEmailService` is only created when `app.aws.ses.enabled=true`:

```java
@Service
@ConditionalOnProperty(name = "app.aws.ses.enabled", havingValue = "true")
public class SesEmailService {
    // Sends email via SES (LocalStack in stage, real SES in prod)
    public void sendEmail(String to, String subject, String body) { ... }
}
```

### AWS Config (Stage Profile)

```yaml
# application-stage.yml
app:
  aws:
    enabled: true
    endpoint: "http://localstack:4566"  # LocalStack unified endpoint
    access-key: "test"
    secret-key: "test"
    region: us-east-1
    s3:
      bucket-name: amazon-demo-stage
      path-style-access: true          # Required for LocalStack S3
    ses:
      enabled: true
      endpoint: "http://localstack:4566"
    sqs:
      endpoint: "http://localstack:4566"
```

---

## LocalStack Health Check

```bash
# Full health response
curl http://localhost:4566/_localstack/health | python3 -m json.tool

# Quick check
curl -s http://localhost:4566/_localstack/health | grep -o '"s3": "[^"]*"'
```

Expected response:
```json
{
  "services": {
    "s3": "running",
    "sqs": "running",
    "sns": "running",
    "ses": "running",
    "secretsmanager": "running",
    "ssm": "running"
  }
}
```

---

## Testing LocalStack Integration

### Integration Test Example

```java
@SpringBootTest
@ActiveProfiles("stage")
@Testcontainers
class ProductServiceS3IntegrationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:3.4")
    ).withServices(S3);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.aws.s3.endpoint", () -> localStack.getEndpoint().toString());
        registry.add("app.aws.access-key", localStack::getAccessKey);
        registry.add("app.aws.secret-key", localStack::getSecretKey);
        registry.add("app.aws.enabled", () -> "true");
    }

    @Test
    void shouldUploadProductImage() {
        // Test S3 upload via LocalStack TestContainers
    }
}
```

---

## Production vs LocalStack Differences

| Aspect          | LocalStack (Stage)           | Real AWS (Prod)              |
|-----------------|------------------------------|------------------------------|
| Credentials     | Any value ("test"/"test")    | Real IAM key/secret or role  |
| S3 URL style    | Path-style required          | Virtual-hosted style default |
| SES delivery    | Captured, not sent           | Real email delivery          |
| SQS durability  | In-memory (unless PERSISTENCE=1) | Durable, replicated      |
| SNS→SQS         | Simulated fan-out            | Real fan-out                 |
| Latency         | ~1ms (local)                 | ~50-200ms                    |
| Cost            | Free                         | Pay per use                  |

---

## Common Issues

### LocalStack not initializing
```bash
# Check logs
docker logs amazon-demo-localstack

# Re-run init manually
docker exec amazon-demo-localstack \
    bash /etc/localstack/init/ready.d/init-localstack.sh
```

### S3 upload fails with "path-style" error
Ensure in `application-stage.yml`:
```yaml
app.aws.s3.path-style-access: true
```

### SQS queue not found
Queue URL format for LocalStack:
```
http://localstack:4566/000000000000/{queue-name}
```

### "Invalid endpoint" error from Spring
Ensure the endpoint URL starts with `http://` or `https://`:
```yaml
app.aws.s3.endpoint: "http://localstack:4566"  # correct
app.aws.s3.endpoint: "localstack:4566"          # wrong
```
