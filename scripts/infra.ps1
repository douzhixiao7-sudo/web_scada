param([ValidateSet('Start','Stop','Check')][string]$Action = 'Start')
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$local = Join-Path $project '.local'
$config = Join-Path $local 'config'
$mysql = Join-Path $local 'tools\mysql-8.4.9-winx64\bin'
$run = Join-Path $project '.run'
New-Item -ItemType Directory -Force -Path $run | Out-Null

function Test-MySql {
    $oldPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & "$mysql\mysql.exe" "--defaults-extra-file=$config\mysql-admin.cnf" --connect-timeout=2 --execute='SELECT 1' *> $null
    $success = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $oldPreference
    return $success
}

if (-not (Test-Path "$config\mysql-admin.cnf")) { throw 'Initialize MySQL first using mysql-init.ps1.' }
if ($Action -eq 'Stop') {
    if (Test-MySql) {
        & "$mysql\mysqladmin.exe" "--defaults-extra-file=$config\mysql-admin.cnf" shutdown
        if ($LASTEXITCODE -ne 0) { throw 'MySQL shutdown failed.' }
    }
    Write-Output 'MySQL stopped.'
    return
}
if ($Action -eq 'Start' -and -not (Test-MySql)) {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 3306)
    try { $listener.Start() } catch { throw 'Port 3306 is occupied by a service that does not accept this project credential.' } finally { $listener.Stop() }
    $server = Start-Process "$mysql\mysqld.exe" -ArgumentList @(('"--defaults-file=' + "$config\mysql.ini" + '"'), '--console') -WindowStyle Hidden -RedirectStandardOutput "$run\mysql.out.log" -RedirectStandardError "$run\mysql.err.log" -PassThru
    for ($attempt=0; $attempt -lt 30; $attempt++) {
        if ($server.HasExited) { throw 'MySQL exited; inspect .run/mysql.err.log.' }
        if (Test-MySql) { break }
        Start-Sleep -Seconds 1
    }
}
if (-not (Test-MySql)) { throw 'MySQL health check failed.' }
Write-Output 'MySQL: SELECT 1 OK (127.0.0.1:3306)'
