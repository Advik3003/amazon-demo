#!/bin/sh
# ============================================================
# LOCALSTACK INITIALIZATION SCRIPT
# ============================================================
# Runs automatically when LocalStack container starts.
# Creates all AWS resources needed for staging environment:
#
#   S3:              File uploads, product images, batch reports
#   SQS:             Order/payment/inventory event queues
#   SNS:             Order and product event topics
#   SES:             Email sending (identity verification)
#   Secrets Manager: App secrets (JWT, DB passwords)
#
# LocalStack endpoint: http://localstack:4566
# Fake AWS account: 000000000000  (LocalStack default)
# ============================================================

set -e

AWS_CMD="aws --endpoint-url=http://localhost:4566 --region us-east-1"

echo ""
echo "============================================"
echo "  Initializing LocalStack AWS Resources"
echo "============================================"
echo ""

# -----------------------------------------------------------
# S3 BUCKETS
# -----------------------------------------------------------
echo ">>> Creating S3 Buckets..."

# Main uploads bucket
$AWS_CMD s3 mb s3://amazon-demo-stage || true
$AWS_CMD s3api put-bucket-cors \
  --bucket amazon-demo-stage \
  --cors-configuration '{
    "CORSRules": [{
      "AllowedHeaders": ["*"],
      "AllowedMethods": ["GET","PUT","POST","DELETE","HEAD"],
      "AllowedOrigins": ["*"],
      "ExposeHeaders": ["ETag"],
      "MaxAgeSeconds": 3000
    }]
  }'

# Reports bucket (batch jobs)
$AWS_CMD s3 mb s3://amazon-demo-stage-reports || true

# Static assets bucket
$AWS_CMD s3 mb s3://amazon-demo-stage-static || true

# Pre-create folder structure in uploads bucket
$AWS_CMD s3api put-object --bucket amazon-demo-stage --key product-images/ --content-length 0 || true
$AWS_CMD s3api put-object --bucket amazon-demo-stage --key user-avatars/ --content-length 0 || true
$AWS_CMD s3api put-object --bucket amazon-demo-stage-reports --key sales-reports/ --content-length 0 || true
$AWS_CMD s3api put-object --bucket amazon-demo-stage-reports --key inventory-reports/ --content-length 0 || true

echo "  [OK] S3 buckets: amazon-demo-stage, amazon-demo-stage-reports, amazon-demo-stage-static"

# -----------------------------------------------------------
# SQS QUEUES
# -----------------------------------------------------------
echo ""
echo ">>> Creating SQS Queues..."

# Order processing queue
$AWS_CMD sqs create-queue \
  --queue-name order-notifications \
  --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"30"}' || true

# Payment notification queue
$AWS_CMD sqs create-queue \
  --queue-name payment-notifications \
  --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"30"}' || true

# Inventory alert queue  
$AWS_CMD sqs create-queue \
  --queue-name inventory-alerts \
  --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"30"}' || true

# Dead letter queues
$AWS_CMD sqs create-queue \
  --queue-name order-notifications-dlq \
  --attributes '{"MessageRetentionPeriod":"1209600"}' || true

$AWS_CMD sqs create-queue \
  --queue-name payment-notifications-dlq \
  --attributes '{"MessageRetentionPeriod":"1209600"}' || true

# Order events queue (for async processing)
$AWS_CMD sqs create-queue \
  --queue-name order-events \
  --attributes '{"MessageRetentionPeriod":"86400","VisibilityTimeout":"60"}' || true

echo "  [OK] SQS queues: order-notifications, payment-notifications, inventory-alerts"
echo "  [OK] SQS DLQs:   order-notifications-dlq, payment-notifications-dlq"
echo "  [OK] SQS queue:  order-events"

# -----------------------------------------------------------
# SNS TOPICS
# -----------------------------------------------------------
echo ""
echo ">>> Creating SNS Topics..."

# Order events topic (fan-out to multiple subscribers)
$AWS_CMD sns create-topic --name order-events || true
$AWS_CMD sns create-topic --name product-events || true
$AWS_CMD sns create-topic --name payment-events || true
$AWS_CMD sns create-topic --name inventory-events || true
$AWS_CMD sns create-topic --name user-notifications || true

# Subscribe SQS queues to SNS topics
ORDER_TOPIC_ARN="arn:aws:sns:us-east-1:000000000000:order-events"
ORDER_QUEUE_ARN="arn:aws:sqs:us-east-1:000000000000:order-notifications"

$AWS_CMD sns subscribe \
  --topic-arn "$ORDER_TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$ORDER_QUEUE_ARN" || true

echo "  [OK] SNS topics: order-events, product-events, payment-events, inventory-events, user-notifications"
echo "  [OK] SNS->SQS subscription: order-events -> order-notifications"

# -----------------------------------------------------------
# SES (Email)
# -----------------------------------------------------------
echo ""
echo ">>> Setting up SES Email Identities..."

# Verify email identities (required before sending)
$AWS_CMD ses verify-email-identity --email-address noreply@amazondemo-stage.com || true
$AWS_CMD ses verify-email-identity --email-address support@amazondemo-stage.com || true
$AWS_CMD ses verify-email-identity --email-address test@amazondemo-stage.com || true

# Verify domain (LocalStack simulated)
$AWS_CMD ses verify-domain-identity --domain amazondemo-stage.com || true

echo "  [OK] SES identities: noreply@amazondemo-stage.com, support@amazondemo-stage.com"

# -----------------------------------------------------------
# SECRETS MANAGER
# -----------------------------------------------------------
echo ""
echo ">>> Creating Secrets Manager Secrets..."

# JWT Secret
$AWS_CMD secretsmanager create-secret \
  --name "amazon-demo/stage/jwt-secret" \
  --description "JWT signing secret for staging" \
  --secret-string "stage-env-jwt-secret-amazondemo-min-256-bits-for-hs512-secure" || true

# Database credentials
$AWS_CMD secretsmanager create-secret \
  --name "amazon-demo/stage/db-credentials" \
  --description "Database credentials for staging" \
  --secret-string '{"username":"postgres","password":"postgres_stage_pass","host":"postgres","port":"5432"}' || true

# RabbitMQ credentials
$AWS_CMD secretsmanager create-secret \
  --name "amazon-demo/stage/rabbitmq-credentials" \
  --description "RabbitMQ credentials for staging" \
  --secret-string '{"username":"amazon_demo","password":"rabbitmq_stage_pass"}' || true

# API Keys (dummy for staging)
$AWS_CMD secretsmanager create-secret \
  --name "amazon-demo/stage/payment-api-key" \
  --description "Payment gateway API key for staging" \
  --secret-string "dummy-stage-payment-api-key-not-real" || true

$AWS_CMD secretsmanager create-secret \
  --name "amazon-demo/stage/app-config" \
  --description "Application configuration secrets" \
  --secret-string '{"appName":"Amazon Demo [STAGING]","supportEmail":"stage-support@amazondemo.com"}' || true

echo "  [OK] Secrets: jwt-secret, db-credentials, rabbitmq-credentials, payment-api-key, app-config"

# -----------------------------------------------------------
# IAM (LocalStack simulated - for realistic testing)
# -----------------------------------------------------------
echo ""
echo ">>> Setting up IAM Policies..."

# Create service role policy
$AWS_CMD iam create-policy \
  --policy-name AmazonDemoStagePolicy \
  --policy-document '{
    "Version":"2012-10-17",
    "Statement":[
      {"Effect":"Allow","Action":["s3:*"],"Resource":["arn:aws:s3:::amazon-demo-stage*"]},
      {"Effect":"Allow","Action":["sqs:*"],"Resource":["arn:aws:sqs:us-east-1:000000000000:*"]},
      {"Effect":"Allow","Action":["sns:*"],"Resource":["arn:aws:sns:us-east-1:000000000000:*"]},
      {"Effect":"Allow","Action":["ses:*"],"Resource":["*"]},
      {"Effect":"Allow","Action":["secretsmanager:GetSecretValue"],"Resource":["arn:aws:secretsmanager:us-east-1:000000000000:secret:amazon-demo/*"]}
    ]
  }' || true

echo "  [OK] IAM policy: AmazonDemoStagePolicy"

# -----------------------------------------------------------
# PARAMETER STORE (SSM)
# -----------------------------------------------------------
echo ""
echo ">>> Creating SSM Parameter Store entries..."

$AWS_CMD ssm put-parameter \
  --name "/amazon-demo/stage/app.env" \
  --value "stage" \
  --type "String" \
  --overwrite || true

$AWS_CMD ssm put-parameter \
  --name "/amazon-demo/stage/app.version" \
  --value "1.0.0" \
  --type "String" \
  --overwrite || true

$AWS_CMD ssm put-parameter \
  --name "/amazon-demo/stage/kafka.bootstrap-servers" \
  --value "kafka:29092" \
  --type "String" \
  --overwrite || true

echo "  [OK] SSM parameters: app.env, app.version, kafka.bootstrap-servers"

# -----------------------------------------------------------
# SUMMARY
# -----------------------------------------------------------
echo ""
echo "============================================"
echo "  LocalStack Initialization Complete!"
echo "============================================"
echo ""
echo "S3 Buckets:"
$AWS_CMD s3 ls 2>/dev/null || echo "  (list unavailable)"
echo ""
echo "SQS Queues:"
$AWS_CMD sqs list-queues 2>/dev/null || echo "  (list unavailable)"
echo ""
echo "SNS Topics:"
$AWS_CMD sns list-topics 2>/dev/null || echo "  (list unavailable)"
echo ""
echo "Secrets:"
$AWS_CMD secretsmanager list-secrets --query 'SecretList[].Name' 2>/dev/null || echo "  (list unavailable)"
echo ""
echo "All AWS resources ready for staging environment."
echo ""
