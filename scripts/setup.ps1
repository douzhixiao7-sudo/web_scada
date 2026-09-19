param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\.local\tools'))
$ErrorActionPreference = 'Stop'
$ToolsRoot = [IO.Path]::GetFullPath($ToolsRoot)
$downloadRoot = Join-Path (Split-Path $ToolsRoot -Parent) 'downloads'
New-Item -ItemType Directory -Force -Path $ToolsRoot, $downloadRoot | Out-Null
$toolchain = Get-Content -LiteralPath "$PSScriptRoot\toolchain.json" -Raw | ConvertFrom-Json
foreach ($tool in $toolchain) {
    if (Test-Path -LiteralPath (Join-Path $ToolsRoot $tool.folder)) { continue }
    $archive = Join-Path $downloadRoot ($tool.folder + '.zip')
    Write-Output "Downloading $($tool.name)"
    Invoke-WebRequest -Uri $tool.url -OutFile $archive -UseBasicParsing
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $tool.sha256) {
        throw "Checksum mismatch: $($tool.name)"
    }
    Expand-Archive -LiteralPath $archive -DestinationPath $ToolsRoot
}
. "$PSScriptRoot\env.ps1" -ToolsRoot $ToolsRoot
Push-Location "$ProjectRoot\frontend"
try {
    & $NpmCmd ci --cache $NpmCache --no-audit
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed.' }
} finally { Pop-Location }
& "$PSScriptRoot\build.ps1" -ToolsRoot $ToolsRoot
