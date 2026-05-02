<#
.SYNOPSIS
  Resets the Keycloak eclaims realm and re-syncs PostgreSQL workflow tables
  so that surveyor/adjustor IDs match the canonical values in eclaims-realm.json.

.DESCRIPTION
  Keycloak's --import-realm only imports a realm if it does NOT already exist.
  If Keycloak assigned its own UUIDs on first start (before explicit user "id"
  fields were present in the JSON), those IDs differ from the DB seeds.
  This script fixes that mismatch:

    1. Delete the eclaims realm via Keycloak Admin REST API
    2. Remove phantom (non-canonical) surveyor/adjustor rows from PostgreSQL
    3. Reset claims whose assigned IDs pointed to phantom rows (back to SUBMITTED)
    4. Restart Keycloak - it re-imports eclaims-realm.json with explicit user IDs

  Canonical IDs (must match eclaims-realm.json AND DB init scripts):
    surveyor1  -> 20000000-0000-0000-0000-000000000001
    surveyor2  -> 20000000-0000-0000-0000-000000000002
    adjustor1  -> 30000000-0000-0000-0000-000000000001
    adjustor2  -> 30000000-0000-0000-0000-000000000002

.EXAMPLE
  .\scripts\reset-keycloak-realm.ps1
#>

$ErrorActionPreference = "Stop"

$KC_URL       = "http://localhost:8080"
$KC_ADMIN     = "admin"
$KC_PASSWORD  = "admin"
$REALM        = "eclaims"
$CONTAINER_KC = "eclaims-keycloak"
$CONTAINER_PG = "eclaims-postgres"
$PG_USER      = "eclaims"
$PG_DB        = "eclaims"

$CANONICAL_SURVEYORS = @(
    '20000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000002'
)
$CANONICAL_ADJUSTORS = @(
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002'
)

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "  OK  $msg"   -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "  WARN $msg"  -ForegroundColor Yellow }

# Use a hashtable body -- Invoke-RestMethod URL-encodes it automatically
$tokenBody = @{
    grant_type = "password"
    client_id  = "admin-cli"
    username   = $KC_ADMIN
    password   = $KC_PASSWORD
}

# ---- Step 1: admin token ------------------------------------------------
Write-Step "Step 1 - Obtain Keycloak admin token"
$tokenResp = Invoke-RestMethod `
    -Method POST `
    -Uri "$KC_URL/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body $tokenBody
$token = $tokenResp.access_token
Write-Ok "Token obtained"

# ---- Step 2: delete realm -----------------------------------------------
Write-Step "Step 2 - Delete Keycloak realm '$REALM'"
try {
    Invoke-RestMethod `
        -Method DELETE `
        -Uri "$KC_URL/admin/realms/$REALM" `
        -Headers @{ Authorization = "Bearer $token" } | Out-Null
    Write-Ok "Realm '$REALM' deleted"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 404) {
        Write-Warn "Realm '$REALM' not found (first run?) - continuing"
    } else {
        throw
    }
}

# ---- Step 3: clean phantom DB rows --------------------------------------
Write-Step "Step 3 - Remove phantom workflow rows and reset affected claims"

$surveyorList = ($CANONICAL_SURVEYORS | ForEach-Object { "'$_'" }) -join ","
$adjustorList = ($CANONICAL_ADJUSTORS | ForEach-Object { "'$_'" }) -join ","

$sql = "DELETE FROM workflow.assignments WHERE surveyor_id::TEXT NOT IN ($surveyorList);" +
       "UPDATE claims.claims SET assigned_surveyor_id = NULL, status = 'SUBMITTED', updated_at = NOW()" +
       "  WHERE assigned_surveyor_id IS NOT NULL AND assigned_surveyor_id NOT IN ($surveyorList);" +
       "UPDATE claims.claims SET assigned_adjustor_id = NULL, updated_at = NOW()" +
       "  WHERE assigned_adjustor_id IS NOT NULL AND assigned_adjustor_id NOT IN ($adjustorList);" +
       "DELETE FROM workflow.surveyors WHERE id::TEXT NOT IN ($surveyorList);" +
       "DELETE FROM workflow.adjustors WHERE id::TEXT NOT IN ($adjustorList);" +
       "SELECT 'surveyors' AS tbl, id, name, email, region FROM workflow.surveyors" +
       " UNION ALL SELECT 'adjustors', id, name, email, region FROM workflow.adjustors ORDER BY tbl, name;"

Write-Host "  Running cleanup SQL inside container '$CONTAINER_PG'..."
$sql | docker exec -i $CONTAINER_PG psql -U $PG_USER -d $PG_DB
if ($LASTEXITCODE -ne 0) { throw "psql cleanup failed (exit $LASTEXITCODE)" }
Write-Ok "Phantom rows removed"

# ---- Step 4: restart Keycloak -------------------------------------------
Write-Step "Step 4 - Restart Keycloak (triggers realm re-import)"
docker restart $CONTAINER_KC | Out-Null
Write-Ok "Container '$CONTAINER_KC' restarting..."

# ---- Step 5: wait for ready ---------------------------------------------
Write-Step "Step 5 - Wait for Keycloak to accept admin logins"
$maxWait  = 120
$interval = 5
$waited   = 0
$ready    = $false

while ($waited -lt $maxWait) {
    Start-Sleep -Seconds $interval
    $waited += $interval
    Write-Host "  Waiting... ($waited / ${maxWait}s)" -NoNewline
    try {
        $check = Invoke-RestMethod `
            -Method POST `
            -Uri "$KC_URL/realms/master/protocol/openid-connect/token" `
            -ContentType "application/x-www-form-urlencoded" `
            -Body $tokenBody `
            -ErrorAction Stop
        if ($check.access_token) {
            $ready = $true
            Write-Host ""
            break
        }
    } catch {
        Write-Host "  (not ready yet)"
    }
}

if (-not $ready) { throw "Keycloak did not become ready within ${maxWait}s" }
Write-Ok "Keycloak is ready"

# ---- Step 6: verify IDs -------------------------------------------------
Write-Step "Step 6 - Verify imported user IDs"
$newToken = (Invoke-RestMethod `
    -Method POST `
    -Uri "$KC_URL/realms/master/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body $tokenBody).access_token

$users = Invoke-RestMethod `
    -Uri "$KC_URL/admin/realms/$REALM/users?max=50" `
    -Headers @{ Authorization = "Bearer $newToken" }

$allOk = $true
foreach ($u in $users) {
    $expected = switch ($u.username) {
        "surveyor1"    { "20000000-0000-0000-0000-000000000001" }
        "surveyor2"    { "20000000-0000-0000-0000-000000000002" }
        "adjustor1"    { "30000000-0000-0000-0000-000000000001" }
        "adjustor2"    { "30000000-0000-0000-0000-000000000002" }
        default        { $null }
    }
    if ($expected -and $u.id -ne $expected) {
        Write-Warn "MISMATCH $($u.username): got $($u.id), expected $expected"
        $allOk = $false
    } else {
        Write-Ok "$($u.username.PadRight(15)) id=$($u.id)"
    }
}

if ($allOk) {
    Write-Host "`nAll user IDs match the DB seeds." -ForegroundColor Green
} else {
    Write-Host "`nSome IDs still mismatched - check eclaims-realm.json user 'id' fields." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "---------------------------------------------------" -ForegroundColor DarkCyan
Write-Host "  Next steps:" -ForegroundColor DarkCyan
Write-Host "  1. Log in as surveyor1 / adjustor1 (Test@1234)" -ForegroundColor DarkCyan
Write-Host "     WorkforceProvisioningFilter confirms their rows." -ForegroundColor DarkCyan
Write-Host "  2. Drop off a vehicle and verify My Assignments" -ForegroundColor DarkCyan
Write-Host "     shows the claim - IDs now match throughout." -ForegroundColor DarkCyan
Write-Host "---------------------------------------------------" -ForegroundColor DarkCyan
