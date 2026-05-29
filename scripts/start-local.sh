#!/bin/bash
# ============================================================
# START LOCAL ENVIRONMENT
# ============================================================
# Usage: ./scripts/start-local.sh [--build] [--clean]
#
# Flags:
#   --build   Rebuild Docker images before starting
#   --clean   Remove volumes (fresh database state)
#   --infra   Start only infrastructure (no Spring services)
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/environments/.env.local"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

echo ""
echo "============================================"
echo "  Amazon Demo - LOCAL Environment"
echo "============================================"
echo ""

# Parse flags
BUILD_FLAG=""
CLEAN_FLAG=false
INFRA_ONLY=false

for arg in "$@"; do
    case $arg in
        --build) BUILD_FLAG="--build" ;;
        --clean) CLEAN_FLAG=true ;;
        --infra) INFRA_ONLY=true ;;
    esac
done

# Load env file
if [ -f "$ENV_FILE" ]; then
    echo "[INFO] Loading: $ENV_FILE"
    export $(grep -v '^#' "$ENV_FILE" | xargs)
else
    echo "[WARN] No .env.local found, using defaults"
fi

cd "$PROJECT_ROOT"

# Clean volumes if requested
if [ "$CLEAN_FLAG" = true ]; then
    echo "[WARN] Removing all volumes (fresh start)..."
    docker compose --env-file "$ENV_FILE" down -v 2>/dev/null || true
fi

if [ "$INFRA_ONLY" = true ]; then
    echo "[INFO] Starting infrastructure only (postgres, mongo, redis, kafka, rabbitmq)..."
    docker compose --env-file "$ENV_FILE" up -d \
        postgres mongo redis zookeeper kafka kafka-ui rabbitmq mailhog localstack $BUILD_FLAG
else
    echo "[INFO] Building services..."
    # Build JAR files first
    cd backend && mvn package -DskipTests -q && cd ..

    echo "[INFO] Starting all services (profile: local)..."
    docker compose --env-file "$ENV_FILE" up -d $BUILD_FLAG
fi

echo ""
echo "============================================"
echo "  Services starting up..."
echo "  Wait ~2 minutes for all services"
echo "============================================"
echo ""
echo "  API Gateway:      http://localhost:8080"
echo "  Eureka Dashboard: http://localhost:8761"
echo "  Config Server:    http://localhost:8888"
echo "  Kafka UI:         http://localhost:8090"
echo "  RabbitMQ Mgmt:    http://localhost:15672 (guest/guest)"
echo "  Mailhog UI:       http://localhost:8025"
echo "  LocalStack:       http://localhost:4566"
echo "  Frontend:         http://localhost:3000"
echo ""
echo "  Swagger UIs:"
echo "    Auth:     http://localhost:8080/auth-service/swagger-ui.html"
echo "    Products: http://localhost:8080/product-service/swagger-ui.html"
echo "    Orders:   http://localhost:8080/order-service/swagger-ui.html"
echo ""
echo "  Profile: LOCAL | AWS: DISABLED | LocalStack: RUNNING"
echo ""
