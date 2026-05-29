#!/bin/bash
# ============================================================
# STOP ALL ENVIRONMENTS
# ============================================================
# Usage: ./scripts/stop-all.sh [--volumes]
#
# Flags:
#   --volumes   Also remove all Docker volumes (data loss!)
# ============================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

VOLUMES_FLAG=""
for arg in "$@"; do
    case $arg in --volumes) VOLUMES_FLAG="-v" ;; esac
done

cd "$PROJECT_ROOT"

echo "Stopping all environments..."

# Stop local
docker compose -f docker-compose.yml down $VOLUMES_FLAG 2>/dev/null && echo "  [OK] local stopped" || true

# Stop stage
docker compose -f docker-compose.stage.yml down $VOLUMES_FLAG 2>/dev/null && echo "  [OK] stage stopped" || true

# Stop prod
docker compose -f docker-compose.yml -f docker-compose.prod.yml down $VOLUMES_FLAG 2>/dev/null && echo "  [OK] prod stopped" || true

echo ""
echo "All environments stopped."
if [ -n "$VOLUMES_FLAG" ]; then
    echo "WARNING: All volumes removed (data deleted)."
fi
