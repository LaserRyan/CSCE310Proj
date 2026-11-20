# Opens the customer desktop app.
. "$PSScriptRoot\common.ps1"
Set-Location $Root
.\mvnw.cmd -q -pl clients/customer-app javafx:run
