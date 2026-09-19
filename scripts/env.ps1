param([string]$ToolsRoot = (Join-Path $PSScriptRoot '..\.local\tools'))
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path $PSScriptRoot -Parent
$ToolsRoot = [IO.Path]::GetFullPath($ToolsRoot)
$JavaRoot = Join-Path $ToolsRoot 'jdk-21.0.12.1+1'
$NodeRoot = Join-Path $ToolsRoot 'node-v24.21.0-win-x64'
$MavenRoot = Join-Path $ToolsRoot 'apache-maven-3.9.16'
$JavaExe = Join-Path $JavaRoot 'bin\java.exe'
$NodeExe = Join-Path $NodeRoot 'node.exe'
$MavenCmd = Join-Path $MavenRoot 'bin\mvn.cmd'
$NpmCmd = Join-Path $NodeRoot 'npm.cmd'
foreach ($tool in @($JavaExe, $NodeExe, $MavenCmd, $NpmCmd)) {
    if (-not (Test-Path -LiteralPath $tool)) { throw "Missing tool: $tool. Run scripts/setup.ps1 first." }
}
$env:JAVA_HOME = $JavaRoot
$env:PATH = "$NodeRoot;$JavaRoot\bin;$MavenRoot\bin;" + $env:PATH
$WorkRoot = Split-Path $ToolsRoot -Parent
$MavenRepo = Join-Path $WorkRoot 'maven-repository'
$NpmCache = Join-Path $WorkRoot 'npm-cache'
$RunRoot = Join-Path $ProjectRoot '.run'
