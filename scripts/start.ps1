param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\.local\tools'))
. "$PSScriptRoot\env.ps1" -ToolsRoot $ToolsRoot
if (-not (Test-Path "$ProjectRoot\.local\config\application-local.properties")) {
    throw 'Local database configuration missing. Initialize infrastructure first; see README.'
}
$jar = Join-Path $ProjectRoot 'backend\target\scada-server-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first: scripts/build.ps1' }
$vite = Join-Path $ProjectRoot 'frontend\node_modules\vite\bin\vite.js'
if (-not (Test-Path -LiteralPath $vite)) { throw 'Install dependencies first: scripts/setup.ps1' }
foreach ($port in @(8080, 5173)) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $port)
    try { $listener.Start() } catch { throw "Port $port is occupied. Stop the existing service first." } finally { $listener.Stop() }
}
New-Item -ItemType Directory -Force -Path $RunRoot | Out-Null
$started = @()
try {
    $backend = Start-Process -FilePath $JavaExe -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory "$ProjectRoot\backend" -WindowStyle Hidden -RedirectStandardOutput "$RunRoot\backend.out.log" -RedirectStandardError "$RunRoot\backend.err.log" -PassThru
    $started += $backend
    $frontend = Start-Process -FilePath $NodeExe -ArgumentList ('"' + $vite + '"') -WorkingDirectory "$ProjectRoot\frontend" -WindowStyle Hidden -RedirectStandardOutput "$RunRoot\frontend.out.log" -RedirectStandardError "$RunRoot\frontend.err.log" -PassThru
    $started += $frontend
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if ($backend.HasExited -or $frontend.HasExited) { throw "A service exited. See $RunRoot" }
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:5173/api/actuator/health' -TimeoutSec 2
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw "Startup timed out. See $RunRoot" }
    @(
        @{ id = $backend.Id; path = $JavaExe; started = $backend.StartTime.ToUniversalTime().ToString('o') },
        @{ id = $frontend.Id; path = $NodeExe; started = $frontend.StartTime.ToUniversalTime().ToString('o') }
    ) | ConvertTo-Json | Set-Content -LiteralPath "$RunRoot\processes.json" -Encoding UTF8
    Write-Output 'Frontend: http://127.0.0.1:5173'
    Write-Output 'Backend health: http://127.0.0.1:8080/actuator/health'
    Write-Output 'Backend health through frontend proxy: UP'
} catch {
    foreach ($process in $started) { if (-not $process.HasExited) { $process.Kill() } }
    throw
}
