# Starts the databases and the three services, each service in its own window.
. "$PSScriptRoot\common.ps1"
Set-Location $Root

docker compose up -d --wait
if ($LASTEXITCODE -ne 0) { throw "Docker could not start the databases. Is Docker Desktop running?" }

$py = Join-Path $Root ".venv\Scripts\python.exe"
$backend = Join-Path $Root "backend"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='User service :8081'; `$env:JAVA_HOME='$env:JAVA_HOME'; Set-Location '$Root'; .\mvnw.cmd -q -pl backend/user-service exec:java"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='Book service :5001'; Set-Location '$backend'; & '$py' -m book_service"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle='Order service :5002'; Set-Location '$backend'; & '$py' -m order_service"

Write-Host "Started 3 services in separate windows. Wait ~10 seconds, then run:"
Write-Host "  .\scripts\run-customer.ps1   or   .\scripts\run-manager.ps1"
