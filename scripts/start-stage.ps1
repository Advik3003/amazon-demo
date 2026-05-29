# ============================================================
# START STAGING ENVIRONMENT - PowerShell (LocalStack)
# ============================================================
# Usage: .\scripts\start-stage.ps1 [-Build] [-Clean]
# ============================================================

param(
    [switch]$Build,
    [switch]$Clean
)

$ProjectRoot = Split-Path $PSScriptRoot -Parent
$EnvFile = Join-Path $ProjectRoot "environments\.env.stage"
$StageCompose = Join-Path $ProjectRoot "docker-compose.stage.yml"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Amazon Demo - STAGING Environment" -ForegroundColor Cyan
Write-Host "  (LocalStack AWS Simulation)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $EnvFile)) {
    Write-Host "[ERROR] Missing: $EnvFile" -ForegroundColor Red
    exit 1
}

Set-Location $ProjectRoot

if ($Clean) {
    Write-Host "[WARN] Removing staging volumes..." -ForegroundColor Yellow
    docker compose -f $StageCompose --env-file $EnvFile down -v 2>$null
}

Write-Host "[INFO] Building backend services..." -ForegroundColor Green
Set-Location (Join-Path $ProjectRoot "backend")
mvn package -DskipTests -q
Set-Location $ProjectRoot

$buildFlag = if ($Build) { "--build" } else { "" }

Write-Host "[INFO] Starting staging environment (profile: stage)..." -ForegroundColor Green
docker compose -f $StageCompose --env-file $EnvFile up -d $buildFlag

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Staging Environment Starting..." -ForegroundColor Cyan
Write-Host "  Wait ~3 minutes for LocalStack + services" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  API Gateway:      http://localhost:8080" -ForegroundColor White
Write-Host "  LocalStack:       http://localhost:4566" -ForegroundColor White
Write-Host "  Mailhog UI:       http://localhost:8025" -ForegroundColor White
Write-Host "  RabbitMQ Mgmt:    http://localhost:15672" -ForegroundColor White
Write-Host ""
Write-Host "  Check LocalStack health:" -ForegroundColor Yellow
Write-Host "    curl.exe http://localhost:4566/_localstack/health" -ForegroundColor Gray
Write-Host ""
Write-Host "  List S3 buckets (needs AWS CLI):" -ForegroundColor Yellow
Write-Host "    aws --endpoint-url=http://localhost:4566 s3 ls" -ForegroundColor Gray
Write-Host ""
Write-Host "  Profile: STAGING | AWS: LocalStack (SIMULATED)" -ForegroundColor Green
Write-Host ""
