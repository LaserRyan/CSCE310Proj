# One-time setup: starts the databases, installs Python packages, builds the Java code.
. "$PSScriptRoot\common.ps1"
Set-Location $Root

Write-Host "Starting MySQL and MongoDB in Docker..."
docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw "Docker could not start the databases. Is Docker Desktop running?" }

if (-not (Test-Path ".venv")) {
    Write-Host "Creating Python environment..."
    python -m venv .venv
}
Write-Host "Installing Python packages..."
.\.venv\Scripts\python.exe -m pip install -q -r backend\requirements.txt

Write-Host "Building Java code (first run downloads Maven and libraries)..."
.\mvnw.cmd -q install -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Java build failed." }

Write-Host "Setup done. Next: .\scripts\start-backend.ps1"
