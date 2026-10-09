param(
    [string]$MySql = 'D:\mysql8\bin\mysql.exe',
    [string]$InputFile = (Join-Path $PSScriptRoot 'web_scada_mvp.sql'),
    [string]$Username = 'root',
    [Security.SecureString]$Password
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $MySql)) { throw "mysql.exe not found: $MySql" }
if (-not (Test-Path -LiteralPath $InputFile)) { throw "SQL export not found: $InputFile" }
if (-not $Password) { $Password = Read-Host "MySQL password for $Username" -AsSecureString }

$plainPassword = [Net.NetworkCredential]::new('', $Password).Password
$tempConfig = Join-Path ([IO.Path]::GetTempPath()) ("web-scada-import-{0}.cnf" -f [Guid]::NewGuid().ToString('N'))
try {
    $escapedUser = $Username.Replace('\', '\\').Replace('"', '\"')
    $escapedPassword = $plainPassword.Replace('\', '\\').Replace('"', '\"')
    @(
        '[client]',
        'host=127.0.0.1',
        'port=3306',
        "user=`"$escapedUser`"",
        "password=`"$escapedPassword`"",
        'default-character-set=utf8mb4'
    ) | Set-Content -LiteralPath $tempConfig -Encoding UTF8

    $sqlPath = [IO.Path]::GetFullPath($InputFile).Replace('\', '/')
    & $MySql "--defaults-extra-file=$tempConfig" --show-warnings "--execute=SOURCE $sqlPath"
    if ($LASTEXITCODE -ne 0) { throw "MySQL import failed with exit code $LASTEXITCODE." }
    Write-Output 'Database web_scada imported successfully.'
} finally {
    $plainPassword = $null
    Remove-Item -LiteralPath $tempConfig -Force -ErrorAction SilentlyContinue
}
