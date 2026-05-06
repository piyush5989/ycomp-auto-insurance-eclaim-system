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

function Test-MavenExe {
  param([string]$MvnPath)
  if (-not (Test-Path -LiteralPath $MvnPath)) { return $false }
  try {
    $out = & cmd.exe /c "`"$MvnPath`" --version 2>&1"
    return ($out -match "Apache Maven")
  } catch {
    return $false
  }
}

function Get-PortableMavenFromTools {
  param([string]$RepoRoot)
  $toolsDir = Join-Path $RepoRoot ".tools"
  if (-not (Test-Path -LiteralPath $toolsDir)) { return $null }
  $dirs = Get-ChildItem -LiteralPath $toolsDir -Directory -Filter "apache-maven-*" -ErrorAction SilentlyContinue |
    Sort-Object { $_.Name } -Descending
  foreach ($d in $dirs) {
    $candidate = Join-Path $d.FullName "bin\mvn.cmd"
    if (Test-Path -LiteralPath $candidate) { return $candidate }
  }
  return $null
}

function Install-PortableMaven {
  param([string]$RepoRoot)
  $toolsDir = Join-Path $RepoRoot ".tools"
  New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
  $version = "3.9.9"
  $zipName = "apache-maven-$version-bin.zip"
  $url = "https://archive.apache.org/dist/maven/maven-3/$version/binaries/$zipName"
  $zipPath = Join-Path $toolsDir $zipName
  Write-Host "Downloading Apache Maven $version to $toolsDir ..."
  Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
  Expand-Archive -LiteralPath $zipPath -DestinationPath $toolsDir -Force
  Remove-Item -LiteralPath $zipPath -Force
  $mvnCmd = Join-Path $toolsDir "apache-maven-$version\bin\mvn.cmd"
  if (-not (Test-Path -LiteralPath $mvnCmd)) {
    throw "Maven install failed: $mvnCmd not found after extract."
  }
  return $mvnCmd
}

function Resolve-MavenCommand {
  param([string]$RepoRoot)

  $mvnw = Join-Path $RepoRoot "mvnw.cmd"
  if (Test-Path -LiteralPath $mvnw) {
    Write-Host "Using Maven wrapper: $mvnw"
    return $mvnw
  }

  $portable = Get-PortableMavenFromTools -RepoRoot $RepoRoot
  if ($portable -and (Test-MavenExe $portable)) {
    Write-Host "Using portable Maven: $portable"
    return $portable
  }

  $sys = Get-Command mvn -ErrorAction SilentlyContinue
  if ($sys -and $sys.Source -and (Test-MavenExe $sys.Source)) {
    Write-Host "Using Maven on PATH: $($sys.Source)"
    return $sys.Source
  }

  Write-Host "No working Maven found. Installing portable Maven under .tools ..."
  $installed = Install-PortableMaven -RepoRoot $RepoRoot
  if (-not (Test-MavenExe $installed)) {
    throw "Installed Maven at $installed does not run. Check network or antivirus."
  }
  Write-Host "Using portable Maven: $installed"
  return $installed
}

try {
  $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
  $repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
  Set-Location $repoRoot

  $mvn = Resolve-MavenCommand -RepoRoot $repoRoot

  Stop-ProcessOnPort -Port 8090

  Write-Host "Starting backend on http://localhost:8090 ..."
  Write-Host "Installing all upstream modules ..."
  & $mvn -pl app/eclaims-api -am install "-DskipTests"
  if ($LASTEXITCODE -ne 0) { throw "Module install failed." }

  Write-Host "Launching Spring Boot ..."
  & $mvn -pl app/eclaims-api spring-boot:run "-Dspring-boot.run.profiles=local"
} catch {
  Write-Error $_
  exit 1
}
