#!/bin/bash
# ============================================================
# START PRODUCTION ENVIRONMENT (Simulated)
# ============================================================
# Usage: ./scripts/start-prod.sh [--build]
#
# IMPORTANT:
#   - This uses DUMMY/SIMULATED AWS credentials for demo
#   - In real production, use IAM roles or real credentials
#   - The prod profile uses `ddl-auto: validate` (safe for prod)
#   - All services restart automatically (restart: always)
#
# Simulated AWS services (dummy endpoints for learning):
#   - S3: Uses dummy bucket names
#   - SQS: Uses dummy queue URLs
#   - SES: Uses dummy SMTP credentials
#
# For a real production deployment:
#   1. Replace all DUMMY_* values in environments/.env.prod
#   2. Use Kubernetes (k8s/) instead of Docker Compose
#   3. Use AWS IAM roles for credentials, not static keys
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/environments/.env.prod"
BASE_COMPOSE="$PROJECT_ROOT/docker-compose.yml"
PROD_COMPOSE="$PROJECT_ROOT/docker-compose.prod.yml"

echo ""
echo "============================================"
echo "  Amazon Demo - PRODUCTION Environment"
echo "  (Simulated - Demo Project)"
echo "============================================"
echo ""
echo "  WARNING: This uses dummy AWS credentials!"
echo "  For real production: update environments/.env.prod"
echo ""

if [ ! -f "$ENV_FILE" ]; then
    echo "[ERROR] Missing: $ENV_FILE"
    exit 1
fi

# Check for obvious dummy values still in place
if grep -q "CHANGE_IN_PROD\|REPLACE_WITH" "$ENV_FILE" 2>/dev/null; then
    echo "[WARN] Detected placeholder values in .env.prod"
    echo "       These are dummy values for the demo project."
    echo "       In real production, replace them with actual credentials."
    echo ""
fi

BUILD_FLAG=""
for arg in "$@"; do
    case $arg in --build) BUILD_FLAG="--build" ;; esac
done

cd "$PROJECT_ROOT"

echo "[INFO] Building backend services..."
cd backend && mvn package -DskipTests -q && cd ..

echo "[INFO] Starting production environment..."
echo "[INFO] Profile: prod | AWS: SIMULATED (dummy endpoints)"
echo ""

docker compose \
    -f "$BASE_COMPOSE" \
    -f "$PROD_COMPOSE" \
    --env-file "$ENV_FILE" \
    up -d $BUILD_FLAG

echo ""
echo "============================================"
echo "  Production Environment Starting..."
echo "============================================"
echo ""
echo "  API Gateway:      http://localhost:${API_GATEWAY_PORT:-80}"
echo "  Eureka Dashboard: http://localhost:${EUREKA_PORT:-8761}"
echo "  Config Server:    http://localhost:${CONFIG_SERVER_PORT:-8888}"
echo ""
echo "  Profile: PRODUCTION | AWS: SIMULATED DUMMY"
echo ""
echo "  Services restart automatically on failure."
echo ""
