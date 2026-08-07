param(
    [string]$Config = "scripts/backend/framework/rename-framework.json",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "rename-framework.py"
$arguments = @($scriptPath, "--config", $Config)

if ($DryRun) {
    $arguments += "--dry-run"
}

$pythonCommand = Get-Command python -ErrorAction SilentlyContinue
if ($null -ne $pythonCommand) {
    & $pythonCommand.Source @arguments
    exit $LASTEXITCODE
}

$pythonCommand = Get-Command py -ErrorAction SilentlyContinue
if ($null -ne $pythonCommand) {
    & $pythonCommand.Source -3 @arguments
    exit $LASTEXITCODE
}

throw "未找到 Python，请先安装 Python 3。"
