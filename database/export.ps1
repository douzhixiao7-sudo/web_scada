param(
    [string]$MySqlDump = 'D:\mysql8\bin\mysqldump.exe',
    [string]$Output = (Join-Path $PSScriptRoot 'web_scada_mvp.sql')
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$propertiesFile = Join-Path $projectRoot '.local\config\application-local.properties'
if (-not (Test-Path -LiteralPath $MySqlDump)) { throw "mysqldump.exe not found: $MySqlDump" }
if (-not (Test-Path -LiteralPath $propertiesFile)) { throw 'Missing .local/config/application-local.properties.' }

$properties = @{}
foreach ($line in Get-Content -LiteralPath $propertiesFile) {
    if ($line -match '^\s*([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2] }
}
$username = $properties['spring.datasource.username']
$password = $properties['spring.datasource.password']
if (-not $username -or $null -eq $password) { throw 'Database username or password is missing.' }

function Escape-OptionValue([string]$Value) {
    return $Value.Replace('\', '\\').Replace('"', '\"')
}

$tempConfig = Join-Path ([IO.Path]::GetTempPath()) ("web-scada-dump-{0}.cnf" -f [Guid]::NewGuid().ToString('N'))
try {
    @(
        '[client]',
        'host=127.0.0.1',
        'port=3306',
        "user=`"$(Escape-OptionValue $username)`"",
        "password=`"$(Escape-OptionValue $password)`"",
        'default-character-set=utf8mb4'
    ) | Set-Content -LiteralPath $tempConfig -Encoding UTF8

    $outputPath = [IO.Path]::GetFullPath($Output)
    New-Item -ItemType Directory -Force -Path (Split-Path $outputPath -Parent) | Out-Null
    & $MySqlDump "--defaults-extra-file=$tempConfig" --databases web_scada --single-transaction --quick --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$outputPath"
    if ($LASTEXITCODE -ne 0) { throw "mysqldump failed with exit code $LASTEXITCODE." }
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $outputPath).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$outputPath.sha256" -Value "$hash  $(Split-Path $outputPath -Leaf)" -Encoding ASCII
    Write-Output "Database export created: $outputPath"
    Write-Output "SHA256: $hash"
} finally {
    Remove-Item -LiteralPath $tempConfig -Force -ErrorAction SilentlyContinue
}
