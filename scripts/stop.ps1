param([switch]$IncludeInfrastructure)
$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $PSScriptRoot '..\.run\processes.json'
if (Test-Path -LiteralPath $stateFile) {
    foreach ($entry in (Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json)) {
        $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
        if ($null -eq $process) { continue }
        $savedStart = ([DateTime]$entry.started).ToUniversalTime()
        if ($process.Path -ne $entry.path -or $process.StartTime.ToUniversalTime() -ne $savedStart) {
            throw "Process identity changed for PID $($entry.id); refusing to stop it."
        }
        Stop-Process -Id $process.Id
        Write-Output "Stopped project process $($entry.id)"
    }
    Remove-Item -LiteralPath $stateFile
} else {
    Write-Output 'No saved project processes.'
}
if ($IncludeInfrastructure) { & "$PSScriptRoot\infra.ps1" -Action Stop }
