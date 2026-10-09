param(
    [ValidateSet('Start','Stop','Check')][string]$Action = 'Start',
    [string]$RedisServer
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runRoot = Join-Path $projectRoot '.run'
$configRoot = Join-Path $projectRoot '.local\config'
$redisDataRoot = Join-Path $runRoot 'redis-data'
$stateFile = Join-Path $runRoot 'infrastructure.json'
$configFile = Join-Path $configRoot 'infrastructure.json'

function Test-TcpPort([int]$Port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(1500) -and $client.Connected
    } catch { return $false } finally { $client.Dispose() }
}

function Test-Redis {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', 6379)
        if (-not $task.Wait(1500) -or -not $client.Connected) { return $false }
        $stream = $client.GetStream()
        $stream.ReadTimeout = 1500
        $request = [Text.Encoding]::ASCII.GetBytes("*1`r`n`$4`r`nPING`r`n")
        $stream.Write($request, 0, $request.Length)
        $buffer = New-Object byte[] 16
        $read = $stream.Read($buffer, 0, $buffer.Length)
        return [Text.Encoding]::ASCII.GetString($buffer, 0, $read).StartsWith('+PONG')
    } catch { return $false } finally { $client.Dispose() }
}

function Resolve-RedisServer {
    if ($RedisServer) {
        $resolved = (Resolve-Path -LiteralPath $RedisServer -ErrorAction Stop).Path
        New-Item -ItemType Directory -Force -Path $configRoot | Out-Null
        @{ redisServer = $resolved } | ConvertTo-Json | Set-Content -LiteralPath $configFile -Encoding UTF8
        return $resolved
    }
    if (Test-Path -LiteralPath $configFile) {
        $saved = (Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json).redisServer
        if ($saved -and (Test-Path -LiteralPath $saved)) { return $saved }
    }
    $localServer = Join-Path $projectRoot '.local\tools\redis\redis-server.exe'
    if (Test-Path -LiteralPath $localServer) { return $localServer }
    $command = Get-Command redis-server.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    throw 'Redis is not running and redis-server.exe was not found. Pass -RedisServer <path> once.'
}

function Stop-ManagedRedis {
    if (-not (Test-Path -LiteralPath $stateFile)) { Write-Output 'Redis: no managed process to stop.'; return }
    $entry = Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $entry.id -ErrorAction SilentlyContinue
    if ($process) {
        $savedStart = ([DateTime]$entry.started).ToUniversalTime()
        if ($process.Path -ne $entry.path -or $process.StartTime.ToUniversalTime() -ne $savedStart) {
            throw "Redis process identity changed for PID $($entry.id); refusing to stop it."
        }
        Stop-Process -Id $process.Id
        Write-Output "Redis: stopped managed process $($process.Id)."
    }
    Remove-Item -LiteralPath $stateFile -Force
}

New-Item -ItemType Directory -Force -Path $runRoot | Out-Null

if ($Action -eq 'Stop') { Stop-ManagedRedis; return }

$mysqlReady = Test-TcpPort 3306
if ($Action -eq 'Start' -and -not $mysqlReady) {
    $service = Get-Service -Name 'MySQL' -ErrorAction SilentlyContinue
    if (-not $service) { throw 'MySQL is not listening on 127.0.0.1:3306 and service MySQL was not found.' }
    if ($service.Status -ne 'Running') { Start-Service -Name 'MySQL' }
    for ($attempt = 0; $attempt -lt 20 -and -not ($mysqlReady = Test-TcpPort 3306); $attempt++) { Start-Sleep -Milliseconds 500 }
}
if (-not $mysqlReady) { throw 'MySQL health check failed on 127.0.0.1:3306.' }
Write-Output 'MySQL: TCP OK (127.0.0.1:3306)'

$redisReady = Test-Redis
if ($Action -eq 'Start' -and -not $redisReady) {
    $serverPath = Resolve-RedisServer
    New-Item -ItemType Directory -Force -Path $redisDataRoot | Out-Null
    $process = Start-Process -FilePath $serverPath -ArgumentList @('--bind','127.0.0.1','--port','6379','--save','""','--appendonly','no','--dir',('"' + $redisDataRoot + '"')) -WorkingDirectory $redisDataRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runRoot 'redis.out.log') -RedirectStandardError (Join-Path $runRoot 'redis.err.log') -PassThru
    for ($attempt = 0; $attempt -lt 20 -and -not ($redisReady = Test-Redis); $attempt++) {
        if ($process.HasExited) { throw 'Redis exited during startup; inspect .run/redis.err.log.' }
        Start-Sleep -Milliseconds 500
    }
    if ($redisReady) {
        @{ id = $process.Id; path = $process.Path; started = $process.StartTime.ToUniversalTime().ToString('o') } | ConvertTo-Json | Set-Content -LiteralPath $stateFile -Encoding UTF8
    }
}
if (-not $redisReady) { throw 'Redis PING health check failed on 127.0.0.1:6379.' }
if ($RedisServer) { Resolve-RedisServer | Out-Null }
Write-Output 'Redis: PONG (127.0.0.1:6379)'
