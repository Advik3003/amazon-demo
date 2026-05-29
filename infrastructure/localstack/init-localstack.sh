#!/bin/bash
# Initialize LocalStack resources (S3 buckets, SQS queues)
# This runs when LocalStack starts

echo "Initializing LocalStack resources..."

# Create S3 bucket for file uploads
awslocal s3 mb s3://amazon-demo-uploads
awslocal s3api put-bucket-cors --bucket amazon-demo-uploads --cors-configuration '{
  "CORSRules": [{
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "PUT", "POST", "DELETE"],
    "AllowedOrigins": ["*"],
    "MaxAgeSeconds": 3000
  }]
}'

# Create SQS queues
awslocal sqs create-queue --queue-name order-notifications
awslocal sqs create-queue --queue-name payment-notifications
awslocal sqs create-queue --queue-name inventory-alerts

echo "LocalStack resources created successfully"
echo "S3 bucket: amazon-demo-uploads"
echo "SQS queues: order-notifications, payment-notifications, inventory-alerts"
