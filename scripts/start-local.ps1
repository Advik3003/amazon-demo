# ============================================================
# START LOCAL ENVIRONMENT (PowerShell)
# ============================================================
# Usage: .\scripts\start-local.ps1 [-Build] [-Clean] [-InfraOnly]
# ============================================================

param(
    [switch]$Build,
    [switch]$Clean,
    [switch]$InfraOnly
)

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$EnvFile = Join-Path $ProjectRoot "environments\.env.local"
$ComposeFile = Join-Path $ProjectRoot "docker-compose.yml"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Amazon Demo - LOCAL Environment" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $EnvFile)) {
    Write-Host "[WARN] No .env.local found, using defaults" -ForegroundColor Yellow
}

Set-Location $ProjectRoot

if ($Clean) {
    Write-Host "[WARN] Removing all volumes (fresh start)..." -ForegroundColor Yellow
    docker compose --env-file $EnvFile down -v 2>$null
}

if (-not $InfraOnly) {
    Write-Host "[INFO] Building backend services..." -ForegroundColor Green
    Set-Location (Join-Path $ProjectRoot "backend")
    mvn package -DskipTests -q
    Set-Location $ProjectRoot
}

$buildFlag = if ($Build) { "--build" } else { "" }

if ($InfraOnly) {
    Write-Host "[INFO] Starting infrastructure only..." -ForegroundColor Green
    docker compose --env-file $EnvFile up -d postgres mongo redis zookeeper kafka kafka-ui rabbitmq mailhog localstack $buildFlag
} else {
    Write-Host "[INFO] Starting all services (profile: local)..." -ForegroundColor Green
    docker compose --env-file $EnvFile up -d $buildFlag
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Services starting up (wait ~2 minutes)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  API Gateway:      http://localhost:8080" -ForegroundColor White
Write-Host "  Eureka Dashboard: http://localhost:8761" -ForegroundColor White
Write-Host "  Config Server:    http://localhost:8888" -ForegroundColor White
Write-Host "  Kafka UI:         http://localhost:8090" -ForegroundColor White
Write-Host "  RabbitMQ Mgmt:    http://localhost:15672 (guest/guest)" -ForegroundColor White
Write-Host "  Mailhog UI:       http://localhost:8025" -ForegroundColor White
Write-Host "  LocalStack:       http://localhost:4566" -ForegroundColor White
Write-Host "  Frontend:         http://localhost:3000" -ForegroundColor White
Write-Host ""
Write-Host "  Profile: LOCAL | AWS: DISABLED | LocalStack: RUNNING" -ForegroundColor Green
Write-Host ""
