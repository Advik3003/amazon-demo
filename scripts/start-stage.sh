#!/bin/bash
# ============================================================
# START STAGING ENVIRONMENT (LocalStack)
# ============================================================
# Usage: ./scripts/start-stage.sh [--build] [--clean]
#
# What this does:
#   1. Starts all infrastructure containers
#   2. Starts LocalStack with S3, SQS, SNS, SES, Secrets Manager
#   3. LocalStack init script creates all AWS resources
#   4. Starts Spring Boot services with stage profile
#   5. All AWS calls go to LocalStack at localhost:4566
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/environments/.env.stage"
BASE_COMPOSE="$PROJECT_ROOT/docker-compose.yml"
STAGE_COMPOSE="$PROJECT_ROOT/docker-compose.stage.yml"

echo ""
echo "============================================"
echo "  Amazon Demo - STAGING Environment"
echo "  (LocalStack AWS Simulation)"
echo "============================================"
echo ""

# Parse flags
BUILD_FLAG=""
CLEAN_FLAG=false

for arg in "$@"; do
    case $arg in
        --build) BUILD_FLAG="--build" ;;
        --clean) CLEAN_FLAG=true ;;
    esac
done

if [ ! -f "$ENV_FILE" ]; then
    echo "[ERROR] Missing: $ENV_FILE"
    echo "  Please create the staging .env file first."
    exit 1
fi

echo "[INFO] Loading: $ENV_FILE"
cd "$PROJECT_ROOT"

if [ "$CLEAN_FLAG" = true ]; then
    echo "[WARN] Removing staging volumes (fresh start)..."
    docker compose -f "$STAGE_COMPOSE" --env-file "$ENV_FILE" down -v 2>/dev/null || true
fi

echo "[INFO] Building backend services..."
cd backend && mvn package -DskipTests -q && cd ..

echo ""
echo "[INFO] Starting staging environment..."
echo "[INFO] Profile: stage | AWS: LocalStack at http://localstack:4566"
echo ""

docker compose \
    -f "$STAGE_COMPOSE" \
    --env-file "$ENV_FILE" \
    up -d $BUILD_FLAG

echo ""
echo "============================================"
echo "  Staging Environment Starting..."
echo "  Wait ~3 minutes for LocalStack + services"
echo "============================================"
echo ""
echo "  API Gateway:      http://localhost:8080"
echo "  Eureka Dashboard: http://localhost:8761"
echo "  Config Server:    http://localhost:8888"
echo "  LocalStack:       http://localhost:4566"
echo "  Mailhog UI:       http://localhost:8025"
echo "  RabbitMQ Mgmt:    http://localhost:15672"
echo ""
echo "  LocalStack AWS Resources (after init):"
echo "    S3:              aws --endpoint-url=http://localhost:4566 s3 ls"
echo "    SQS:             aws --endpoint-url=http://localhost:4566 sqs list-queues"
echo "    SNS:             aws --endpoint-url=http://localhost:4566 sns list-topics"
echo "    Secrets:         aws --endpoint-url=http://localhost:4566 secretsmanager list-secrets"
echo ""
echo "  Profile: STAGING | AWS: LocalStack (SIMULATED) | SES: LocalStack"
echo ""
echo "  Check LocalStack health:"
echo "    curl http://localhost:4566/_localstack/health"
echo ""
