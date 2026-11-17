$ErrorActionPreference='Stop'
$taskRoot=Split-Path -Parent $PSScriptRoot
$env:RAG_APP_HOME=$taskRoot
Set-Location -LiteralPath (Join-Path $taskRoot 'backend')
& java.exe '-Djdk.httpclient.disableRetryConnect=true' '-Djdk.httpclient.enableAllMethodRetry=false' -jar target/course-rag-backend.jar
