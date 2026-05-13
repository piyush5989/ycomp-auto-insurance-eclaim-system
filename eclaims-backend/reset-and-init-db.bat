@echo off
echo ================================================
echo eClaims Database Reset and Initialization
echo ================================================
echo.
echo This will:
echo 1. Drop all existing schemas and data
echo 2. Recreate clean database structure
echo 3. Initialize with fresh seed data
echo.
set /p confirm="Are you sure you want to continue? (y/N): "
if /i "%confirm%" neq "y" (
    echo Operation cancelled.
    goto :end
)

echo.
echo Step 1: Resetting database...
psql -h localhost -p 5432 -U eclaims -d eclaims -f "infra\db\reset_database.sql"

if errorlevel 1 (
    echo ERROR: Failed to reset database
    goto :end
)

echo.
echo Step 2: Reinitializing database structure...
psql -h localhost -p 5432 -U eclaims -d eclaims -f "infra\db\init\01_schemas_and_base_tables.sql"

if errorlevel 1 (
    echo ERROR: Failed to create schemas and base tables
    goto :end
)

psql -h localhost -p 5432 -U eclaims -d eclaims -f "infra\db\init\02_enhancements_and_features.sql"

if errorlevel 1 (
    echo ERROR: Failed to apply enhancements
    goto :end
)

psql -h localhost -p 5432 -U eclaims -d eclaims -f "infra\db\init\03_data_integrity_and_constraints.sql"

if errorlevel 1 (
    echo ERROR: Failed to apply constraints
    goto :end
)

psql -h localhost -p 5432 -U eclaims -d eclaims -f "infra\db\init\04_seed_data_for_development.sql"

if errorlevel 1 (
    echo ERROR: Failed to load seed data
    goto :end
)

echo.
echo ================================================
echo ✅ Database reset and initialization complete!
echo ================================================
echo.
echo Your database now has:
echo - Clean schemas and tables
echo - Fresh seed data for testing
echo - All constraints and indexes
echo.

:end
pause