$ErrorActionPreference='Stop'
$taskRoot=[IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
Set-Location -LiteralPath $taskRoot
& node.exe scripts/generate-contracts.mjs --check
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Set-Location -LiteralPath (Join-Path $taskRoot 'frontend')
& npm.cmd ci
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
& npm.cmd run build
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
$taskStatic=[IO.Path]::GetFullPath((Join-Path $taskRoot 'backend/src/main/resources/static'))
$taskBackend=[IO.Path]::GetFullPath((Join-Path $taskRoot 'backend'))+[IO.Path]::DirectorySeparatorChar
if(-not $taskStatic.StartsWith($taskBackend,[StringComparison]::OrdinalIgnoreCase)){throw '静态文件目标不在本项目后端目录'}
if(Test-Path -LiteralPath $taskStatic){Remove-Item -LiteralPath $taskStatic -Recurse -Force}
New-Item -ItemType Directory -Path $taskStatic | Out-Null
Get-ChildItem -LiteralPath (Join-Path $taskRoot 'frontend/dist') | Copy-Item -Destination $taskStatic -Recurse
Set-Location -LiteralPath (Join-Path $taskRoot 'backend')
& .\mvnw.cmd package
if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}
Write-Output '生成 backend/target/course-rag-backend.jar'
