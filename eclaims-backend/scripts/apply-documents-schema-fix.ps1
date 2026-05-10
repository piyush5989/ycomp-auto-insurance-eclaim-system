# Applies 23_documents_backfill_version_status.sql to a running Postgres container.
# Use when your Docker volume was created before that migration or Hibernate ddl-auto left NULL version/status.
# Example: .\scripts\apply-documents-schema-fix.ps1

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$sql = Join-Path $root "infra\db\init\23_documents_backfill_version_status.sql"
if (-not (Test-Path $sql)) {
    Write-Error "Missing file: $sql"
}
Get-Content $sql -Raw | docker exec -i eclaims-postgres psql -U eclaims -d eclaims
Write-Host "Done."
