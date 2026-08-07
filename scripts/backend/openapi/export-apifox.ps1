param(
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepositoryRoot = (Resolve-Path (Join-Path $ScriptDirectory "../../..")).Path
$OpenApiSource = Join-Path $RepositoryRoot "backend-java/model/src/main/resources/openapi-interface.yaml"
$ApplicationYaml = Join-Path $RepositoryRoot "backend-java/starter/src/main/resources/application.yaml"

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $RepositoryRoot "build/apifox"
}
$OpenApiOutput = Join-Path $OutputDirectory "urban-safe-priority-openapi.json"
$AllCollection = Join-Path $OutputDirectory "urban-safe-priority-all.postman_collection.json"
$PrimaryCollection = Join-Path $OutputDirectory "urban-safe-priority-apifox.postman_collection.json"

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "缺少 Node.js，无法运行 OpenAPI 转换工具。"
}
if (-not (Get-Command npx -ErrorAction SilentlyContinue)) {
    throw "缺少 npx，无法运行 OpenAPI 转换工具。"
}
if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
    throw "缺少 Python 3。请确保 python 命令指向 Python 3。"
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

& python (Join-Path $ScriptDirectory "prepare_local_config.py") $ApplicationYaml
if ($LASTEXITCODE -ne 0) {
    throw "本地 application.yaml 准备失败。"
}

& npx --yes "@redocly/cli@1.27.1" lint $OpenApiSource
if ($LASTEXITCODE -ne 0) {
    throw "OpenAPI 校验失败。"
}

& npx --yes "@redocly/cli@1.27.1" bundle $OpenApiSource --output $OpenApiOutput
if ($LASTEXITCODE -ne 0) {
    throw "OpenAPI 聚合失败。"
}

& python (Join-Path $ScriptDirectory "enrich_openapi.py") $OpenApiOutput
if ($LASTEXITCODE -ne 0) {
    throw "OpenAPI ApiFox 元数据补充失败。"
}

& npx --yes --package "openapi-to-postmanv2@6.3.0" openapi2postmanv2 `
    --spec $OpenApiOutput `
    --output $AllCollection `
    --pretty `
    --options "folderStrategy=Tags,parametersResolution=Example,includeAuthInfoInExample=false"
if ($LASTEXITCODE -ne 0) {
    throw "OpenAPI 转 Postman Collection 失败。"
}

& python (Join-Path $ScriptDirectory "generate_apifox_assets.py") `
    --application $ApplicationYaml `
    --output $OutputDirectory
if ($LASTEXITCODE -ne 0) {
    throw "ApiFox 自动验收集合生成失败。"
}

& python (Join-Path $ScriptDirectory "finalize_apifox_export.py") `
    --output $OutputDirectory `
    --openapi $OpenApiOutput
if ($LASTEXITCODE -ne 0) {
    throw "ApiFox 导入结果归一化失败。"
}

Write-Host ""
Write-Host "ApiFox 导入文件已生成："
Write-Host "1. 推荐直接导入：$PrimaryCollection"
Write-Host "2. 全部 OpenAPI 接口集合：$AllCollection"
Write-Host "3. 可选 OpenAPI 文档：$OpenApiOutput"
Write-Host "4. 核心快速验收：$(Join-Path $OutputDirectory 'urban-safe-priority-smoke.postman_collection.json')"
Write-Host "5. 含图片完整验收：$(Join-Path $OutputDirectory 'urban-safe-priority-full.postman_collection.json')"
Write-Host ""
Write-Host "推荐只导入第 1 个 Collection 文件。它同时包含全部 OpenAPI 接口和自动验收目录，"
Write-Host "并已内置项目变量、登录后置提取及逐接口鉴权，无需再导入或选择 Postman Environment。"
