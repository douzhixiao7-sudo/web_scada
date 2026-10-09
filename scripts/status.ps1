$ErrorActionPreference = 'Continue'
& "$PSScriptRoot\infra.ps1" -Action Check

try {
    $health = Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health' -TimeoutSec 3
    Write-Output "Backend: $($health.status) (127.0.0.1:8080)"
} catch { Write-Output 'Backend: DOWN (127.0.0.1:8080)' }

try {
    $response = Invoke-WebRequest 'http://127.0.0.1:5173/' -TimeoutSec 3 -UseBasicParsing
    Write-Output "Frontend: HTTP $($response.StatusCode) (127.0.0.1:5173)"
} catch { Write-Output 'Frontend: DOWN (127.0.0.1:5173)' }
