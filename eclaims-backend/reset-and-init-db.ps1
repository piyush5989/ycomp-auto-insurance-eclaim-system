# eClaims Database Reset and Initialization PowerShell Script

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "eClaims Database Reset and Initialization" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This will:" -ForegroundColor Yellow
Write-Host "1. Drop all existing schemas and data" -ForegroundColor Yellow
Write-Host "2. Recreate clean database structure" -ForegroundColor Yellow
Write-Host "3. Initialize with fresh seed data" -ForegroundColor Yellow
Write-Host ""

$confirm = Read-Host "Are you sure you want to continue? (y/N)"
if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "Operation cancelled." -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Step 1: Checking Docker containers..." -ForegroundColor Green

# Check if Docker is available
try {
    $dockerVersion = docker --version
    Write-Host "Docker found: $dockerVersion" -ForegroundColor Gray
} catch {
    Write-Host "ERROR: Docker is not available. Please make sure Docker is installed and running." -ForegroundColor Red
    exit 1
}

# Check if postgres container is running
$postgresContainer = docker ps --filter "name=eclaims-postgres" --format "{{.Names}}"
if (-not $postgresContainer) {
    Write-Host "Starting PostgreSQL container..." -ForegroundColor Yellow
    docker-compose up -d postgres
    Start-Sleep -Seconds 10  # Wait for container to be ready
}

Write-Host ""
Write-Host "Step 2: Resetting database using Docker..." -ForegroundColor Green

try {
    # Execute reset script via Docker
    docker exec eclaims-postgres psql -U eclaims -d eclaims -c "
    DROP SCHEMA IF EXISTS notifications CASCADE;
    DROP SCHEMA IF EXISTS customers CASCADE;
    DROP SCHEMA IF EXISTS reporting CASCADE;
    DROP SCHEMA IF EXISTS audit CASCADE;
    DROP SCHEMA IF EXISTS payments CASCADE;
    DROP SCHEMA IF EXISTS workshops CASCADE;
    DROP SCHEMA IF EXISTS workflow CASCADE;
    DROP SCHEMA IF EXISTS documents CASCADE;
    DROP SCHEMA IF EXISTS claims CASCADE;
    SELECT 'All schemas dropped successfully' as status;"
    
    if ($LASTEXITCODE -ne 0) { throw "Failed to reset database" }
    
    Write-Host ""
    Write-Host "Step 3: Recreating database structure..." -ForegroundColor Green
    
    # Recreate the database by restarting the container with init scripts
    Write-Host "Stopping postgres container..." -ForegroundColor Gray
    docker-compose stop postgres
    
    Write-Host "Removing postgres container and volume..." -ForegroundColor Gray
    docker-compose rm -f postgres
    docker volume rm eclaims-backend_postgres_data 2>$null
    
    Write-Host "Starting fresh postgres container with initialization scripts..." -ForegroundColor Gray
    docker-compose up -d postgres
    
    # Wait for postgres to be ready
    Write-Host "Waiting for PostgreSQL to initialize..." -ForegroundColor Gray
    do {
        Start-Sleep -Seconds 2
        $ready = docker exec eclaims-postgres pg_isready -U eclaims 2>$null
    } while ($LASTEXITCODE -ne 0)
    
    # Wait a bit more for initialization scripts to complete
    Start-Sleep -Seconds 5
    
    Write-Host ""
    Write-Host "Step 4: Verifying database setup..." -ForegroundColor Green
    
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
        Write-Host "- Clean schemas and tables" -ForegroundColor White
        Write-Host "- Fresh seed data for testing" -ForegroundColor White
        Write-Host "- All constraints and indexes" -ForegroundColor White
        Write-Host ""
        
        # Show some sample data
        Write-Host "Sample seed data loaded:" -ForegroundColor Cyan
        docker exec eclaims-postgres psql -U eclaims -d eclaims -c "SELECT name, region FROM workflow.surveyors;"
        docker exec eclaims-postgres psql -U eclaims -d eclaims -c "SELECT name, provider_type FROM workshops.workshops LIMIT 3;"
    } else {
        throw "Database initialization incomplete. Expected 9 schemas, found $schemaCount"
    }
    
} catch {
    Write-Host ""
    Write-Host "ERROR: $_" -ForegroundColor Red
    Write-Host "Database reset failed. Please check Docker logs:" -ForegroundColor Red
    Write-Host "docker logs eclaims-postgres" -ForegroundColor Yellow
    exit 1
}

Write-Host "Press any key to continue..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")