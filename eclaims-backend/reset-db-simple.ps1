# Simple Database Reset Script - No user interaction required

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "eClaims Database Reset (Automatic)" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "Step 1: Stopping containers..." -ForegroundColor Green
docker-compose down

Write-Host ""
Write-Host "Step 2: Removing database volume..." -ForegroundColor Green
docker volume rm eclaims-backend_postgres_data 2>$null

Write-Host ""
Write-Host "Step 3: Starting fresh PostgreSQL with init scripts..." -ForegroundColor Green
docker-compose up -d postgres

Write-Host ""
Write-Host "Step 4: Waiting for PostgreSQL to initialize..." -ForegroundColor Green
Start-Sleep -Seconds 15

# Wait for postgres to be ready
$maxAttempts = 30
$attempt = 0
do {
    $attempt++
    Start-Sleep -Seconds 2
    $ready = docker exec eclaims-postgres pg_isready -U eclaims 2>$null
    if ($attempt -gt $maxAttempts) {
        Write-Host "ERROR: PostgreSQL did not start within expected time" -ForegroundColor Red
        exit 1
    }
} while ($LASTEXITCODE -ne 0)

Write-Host ""
Write-Host "Step 5: Verifying database setup..." -ForegroundColor Green

# Check if schemas were created
$schemaCheck = docker exec eclaims-postgres psql -U eclaims -d eclaims -t -c "
SELECT COUNT(*) FROM information_schema.schemata 
WHERE schema_name IN ('claims','documents','workflow','workshops','payments','reporting','audit','customers','notifications');"

$schemaCount = $schemaCheck.Trim()
Write-Host "Found $schemaCount schemas" -ForegroundColor Gray

if ([int]$schemaCount -eq 9) {
    Write-Host ""
    Write-Host "================================================" -ForegroundColor Green
    Write-Host "✅ Database reset and initialization complete!" -ForegroundColor Green
    Write-Host "================================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Your database now has:" -ForegroundColor Cyan
    Write-Host "- 9 Clean schemas" -ForegroundColor White
    Write-Host "- All tables and indexes" -ForegroundColor White
    Write-Host "- Fresh seed data" -ForegroundColor White
    Write-Host ""
    
    # Show some sample data
    Write-Host "Sample seed data verification:" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Surveyors:" -ForegroundColor Yellow
    docker exec eclaims-postgres psql -U eclaims -d eclaims -c "SELECT name, region FROM workflow.surveyors;"
    
    Write-Host ""
    Write-Host "Workshops (sample):" -ForegroundColor Yellow
    docker exec eclaims-postgres psql -U eclaims -d eclaims -c "SELECT name, provider_type, city FROM workshops.workshops LIMIT 5;"
    
    Write-Host ""
    Write-Host "Policies:" -ForegroundColor Yellow
    docker exec eclaims-postgres psql -U eclaims -d eclaims -c "SELECT policy_number, customer_email FROM claims.policy_cache;"
    
} else {
    Write-Host "ERROR: Database initialization incomplete. Expected 9 schemas, found $schemaCount" -ForegroundColor Red
    Write-Host "Check Docker logs: docker logs eclaims-postgres" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "🎉 Ready for testing!" -ForegroundColor Green