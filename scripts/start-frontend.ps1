$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath (Join-Path $taskRoot 'frontend')
& npm.cmd ci
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& npm.cmd run dev
