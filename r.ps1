param([switch]$Logs, [string]$Device)
& "$PSScriptRoot\run-debug.ps1" -Logs:$Logs -Device $Device
