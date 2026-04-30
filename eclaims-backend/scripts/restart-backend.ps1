$ErrorActionPreference = "Stop"

function Stop-ProcessOnPort {
  param(
    [Parameter(Mandatory = $true)]
    [int]$Port
  )

  $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
  if (-not $conns) {
    Write-Host "No listener found on port $Port."
    return
  }

  $procIds = $conns | Select-Object -ExpandProperty OwningProcess -Unique
  foreach ($procId in $procIds) {
    try {
      $proc = Get-Process -Id $procId -ErrorAction Stop
      Write-Host "Stopping PID $procId ($($proc.ProcessName)) listening on port $Port..."
      Stop-Process -Id $procId -Force -ErrorAction Stop
    } catch {
      Write-Warning "Failed to stop PID $procId on port $Port. $($_.Exception.Message)"
    }
  }

  Start-Sleep -Milliseconds 500
}

try {
  $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
  $repoRoot = Resolve-Path (Join-Path $scriptDir "..")
  Set-Location $repoRoot

  Stop-ProcessOnPort -Port 8090

  Write-Host "Starting backend on http://localhost:8090 ..."
  # Step 1: install all upstream modules (including any newly added ones like eclaims-customers)
  # into the local Maven repo so eclaims-api can resolve them.
  # -am = also-make (builds transitive dependencies), -DskipTests = fast install.
  Write-Host "Installing all upstream modules ..."
  & .\mvnw.cmd -pl app/eclaims-api -am install "-DskipTests"
  if ($LASTEXITCODE -ne 0) { throw "Module install failed." }

  # Step 2: run only the API application.
  Write-Host "Launching Spring Boot ..."
  & .\mvnw.cmd -pl app/eclaims-api spring-boot:run "-Dspring-boot.run.profiles=local"
} catch {
  Write-Error $_
  exit 1
}

