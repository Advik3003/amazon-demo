#!/bin/bash
# ============================================================
# CHECK LOCALSTACK RESOURCES
# ============================================================
# Verifies all AWS resources were created in LocalStack.
# Run after starting the staging environment.
#
# Usage: ./scripts/check-localstack.sh
# ============================================================

LOCALSTACK_URL="${1:-http://localhost:4566}"
AWS_CMD="aws --endpoint-url=$LOCALSTACK_URL --region us-east-1"

echo ""
echo "============================================"
echo "  LocalStack Resource Check"
echo "  Endpoint: $LOCALSTACK_URL"
echo "============================================"
echo ""

# Health check
echo ">>> Health Check:"
curl -sf "$LOCALSTACK_URL/_localstack/health" | python3 -m json.tool 2>/dev/null || \
    curl -sf "$LOCALSTACK_URL/_localstack/health" || echo "  [FAIL] LocalStack not reachable"
echo ""

# S3 Buckets
echo ">>> S3 Buckets:"
$AWS_CMD s3 ls 2>/dev/null && echo "" || echo "  [FAIL] Cannot list S3 buckets"

# SQS Queues
echo ">>> SQS Queues:"
$AWS_CMD sqs list-queues --output table 2>/dev/null || echo "  [FAIL] Cannot list SQS queues"
echo ""

# SNS Topics
echo ">>> SNS Topics:"
$AWS_CMD sns list-topics --output table 2>/dev/null || echo "  [FAIL] Cannot list SNS topics"
echo ""

# SES Verified Identities
echo ">>> SES Verified Identities:"
$AWS_CMD ses list-identities --output table 2>/dev/null || echo "  [FAIL] Cannot list SES identities"
echo ""

# Secrets Manager
echo ">>> Secrets Manager:"
$AWS_CMD secretsmanager list-secrets --query 'SecretList[].Name' --output table 2>/dev/null || echo "  [FAIL] Cannot list secrets"
echo ""

# SSM Parameters
echo ">>> SSM Parameter Store:"
$AWS_CMD ssm describe-parameters --query 'Parameters[].Name' --output table 2>/dev/null || echo "  [FAIL] Cannot list parameters"
echo ""

echo "============================================"
echo "  Check complete."
echo "============================================"
echo ""
echo "  Quick test commands:"
echo "  Upload file:  $AWS_CMD s3 cp test.txt s3://amazon-demo-stage/"
echo "  Send test SQS: $AWS_CMD sqs send-message --queue-url http://localhost:4566/000000000000/order-notifications --message-body 'test'"
echo ""
