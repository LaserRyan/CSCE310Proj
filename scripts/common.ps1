# Shared setup for the start scripts: finds Java and loads settings from .env.

$Root = Split-Path -Parent $PSScriptRoot

# Use JAVA_HOME if it points at a real JDK; otherwise derive it from the java on PATH.
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) { throw "Java 21 was not found. Install a JDK 21 and try again." }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $java.Source)
}

# Optional settings (e.g. email) from .env:  NAME=value, one per line, # for comments.
$envFile = Join-Path $Root ".env"
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            Set-Item -Path "env:$($Matches[1])" -Value $Matches[2]
        }
    }
}
