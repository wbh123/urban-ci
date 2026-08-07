#!/usr/bin/env bash

# 城安智序真实模型 CUDA-only 启动脚本。
# 动态库发现和 CUDAExecutionProvider 检查统一委托给 with-cuda-env.sh。

set -euo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_SERVICE_DIRECTORY="$(cd "${SCRIPT_DIRECTORY}/.." && pwd)"
AI_SERVICE_HOST="${URBAN_SAFE_AI_SERVICE_HOST:-127.0.0.1}"
AI_SERVICE_PORT="${URBAN_SAFE_AI_SERVICE_PORT:-8001}"

cd "${AI_SERVICE_DIRECTORY}"
exec "${SCRIPT_DIRECTORY}/with-cuda-env.sh" python -m uvicorn app.main:app \
  --host "${AI_SERVICE_HOST}" \
  --port "${AI_SERVICE_PORT}"
