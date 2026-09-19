param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\.local\tools'))
. "$PSScriptRoot\env.ps1" -ToolsRoot $ToolsRoot
& $MavenCmd -B -ntp "-Dmaven.repo.local=$MavenRepo" -f "$ProjectRoot\backend\pom.xml" package
if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
Push-Location "$ProjectRoot\frontend"
try {
    & $NpmCmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
} finally { Pop-Location }
