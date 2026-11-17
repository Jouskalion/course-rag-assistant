$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$env:RAG_APP_HOME=$taskRoot
Set-Location -LiteralPath (Join-Path $taskRoot 'backend')
& .\mvnw.cmd spring-boot:run
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
