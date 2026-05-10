#Requires -Version 5.1
<#
.SYNOPSIS
  Truncates all eClaims app-schema tables in Postgres (preserves Keycloak data).

.PARAMETER IncludeRedis
  If set, runs FLUSHALL on the eclaims-redis container (clears caches / dedupe keys).

.EXAMPLE
  .\clean-app-data.ps1
  .\clean-app-data.ps1 -IncludeRedis
#>
param(
    [switch] $IncludeRedis
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$sqlPath = Join-Path $PSScriptRoot 'clean-app-data.sql'

if (-not (Test-Path $sqlPath)) {
    Write-Error "Missing $sqlPath"
}

$running = docker ps --filter "name=eclaims-postgres" --format "{{.Names}}"
if (-not $running) {
    Write-Error "Container eclaims-postgres is not running. Start stack: docker compose up -d (from eclaims-backend)."
}

Get-Content -Raw -LiteralPath $sqlPath | docker exec -i eclaims-postgres psql -U eclaims -d eclaims -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

Write-Host "Postgres: application data cleared; workshops + surveyors re-seeded." -ForegroundColor Green

if ($IncludeRedis) {
    $redis = docker ps --filter "name=eclaims-redis" --format "{{.Names}}"
    if ($redis) {
        docker exec eclaims-redis redis-cli -a redis_dev FLUSHALL 2>$null
        Write-Host "Redis: FLUSHALL completed." -ForegroundColor Green
    } else {
        Write-Warning "eclaims-redis not running — skipped Redis flush."
    }
}
