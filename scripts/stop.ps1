$ErrorActionPreference = 'Stop'
$stateFile = Join-Path $PSScriptRoot '..\.run\processes.json'
if (-not (Test-Path -LiteralPath $stateFile)) { Write-Output 'No saved project processes.'; return }
foreach ($entry in (Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json)) {
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($null -eq $process) { continue }
    if ($process.Path -ne $entry.path -or $process.StartTime.ToUniversalTime().ToString('o') -ne $entry.started) {
        throw "Process identity changed for PID $($entry.id); refusing to stop it."
    }
    Stop-Process -Id $process.Id
    Write-Output "Stopped project process $($entry.id)"
}
Remove-Item -LiteralPath $stateFile
