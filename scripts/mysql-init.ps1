$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$local = Join-Path $project '.local'
$config = Join-Path $local 'config'
$mysql = Join-Path $local 'tools\mysql-8.4.9-winx64'
$data = Join-Path $local 'data\mysql'
if (-not (Test-Path "$mysql\bin\mysqld.exe")) { throw 'Install MySQL 8.4.9 under .local/tools first.' }
if (Test-Path $data) { throw 'MySQL data directory already exists. Initialization will not overwrite it.' }
New-Item -ItemType Directory -Force -Path $config, "$local\data", "$project\.run" | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls.exe $config /inheritance:r /grant:r "${identity}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict local credential directory.' }
function New-LocalPassword {
    $bytes = New-Object byte[] 24
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
$rootPassword = New-LocalPassword
$appPassword = New-LocalPassword
$mysqlPath = $mysql.Replace('\', '/')
$dataPath = $data.Replace('\', '/')
@"
[mysqld]
basedir=$mysqlPath
datadir=$dataPath
bind-address=127.0.0.1
port=3306
mysqlx=OFF
character-set-server=utf8mb4
collation-server=utf8mb4_0900_ai_ci
"@ | Set-Content "$config\mysql.ini" -Encoding ASCII
@"
[client]
host=127.0.0.1
port=3306
user=root
password=$rootPassword
"@ | Set-Content "$config\mysql-admin.cnf" -Encoding ASCII
@{ username='scada_app'; password=$appPassword; database='web_scada'; port=3306 } | ConvertTo-Json | Set-Content "$config\mysql-app.json" -Encoding ASCII
@"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootPassword';
CREATE DATABASE web_scada CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'scada_app'@'localhost' IDENTIFIED BY '$appPassword';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON web_scada.* TO 'scada_app'@'localhost';
"@ | Set-Content "$config\mysql-bootstrap.sql" -Encoding ASCII
$initializer = Start-Process "$mysql\bin\mysqld.exe" -ArgumentList @(('"--defaults-file=' + "$config\mysql.ini" + '"'), '--initialize-insecure', '--console') -WindowStyle Hidden -RedirectStandardOutput "$project\.run\mysql-init.out.log" -RedirectStandardError "$project\.run\mysql-init.log" -PassThru -Wait
if ($initializer.ExitCode -ne 0) { throw 'MySQL initialization failed; see .run/mysql-init.log.' }
$server = Start-Process "$mysql\bin\mysqld.exe" -ArgumentList @(('"--defaults-file=' + "$config\mysql.ini" + '"'), ('"--init-file=' + "$config\mysql-bootstrap.sql" + '"'), '--console') -WindowStyle Hidden -RedirectStandardOutput "$project\.run\mysql.out.log" -RedirectStandardError "$project\.run\mysql.err.log" -PassThru
$ready = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    if ($server.HasExited) { throw 'MySQL exited; see .run/mysql.err.log.' }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & "$mysql\bin\mysql.exe" "--defaults-extra-file=$config\mysql-admin.cnf" --connect-timeout=2 --execute='SELECT 1' *> $null
    $result = $LASTEXITCODE
    $ErrorActionPreference = $previousPreference
    if ($result -eq 0) { $ready=$true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'MySQL bootstrap timed out. Inspect local logs before retrying; do not reinitialize data.' }
& "$mysql\bin\mysqladmin.exe" "--defaults-extra-file=$config\mysql-admin.cnf" shutdown
if ($LASTEXITCODE -ne 0) { throw 'MySQL initial shutdown failed.' }
$server.WaitForExit(10000) | Out-Null
Remove-Item -LiteralPath "$config\mysql-bootstrap.sql"
Write-Output 'MySQL initialized with a project database and random local credentials. No business tables created.'

