# Opens the manager desktop app.
. "$PSScriptRoot\common.ps1"
Set-Location $Root
.\mvnw.cmd -q -pl clients/manager-app javafx:run
