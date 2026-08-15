#!/usr/bin/env bash

# Profile A：CPU 开发机环境准备。
# 创建/复用 ai-service-python/.venv，安装无 CUDA 依赖，生成 dev-cpu .env。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/setup-cn-mirrors.sh"

AI_SERVICE="${PROJECT_ROOT}/ai-service-python"
VENV_DIR="${AI_SERVICE}/.venv"
PYTHON_BIN="${PYTHON_BIN:-python3}"

if [[ ! -x "${VENV_DIR}/bin/python" ]]; then
  echo "创建开发环境：${VENV_DIR}"
  "${PYTHON_BIN}" -m venv "${VENV_DIR}"
fi

"${VENV_DIR}/bin/pip" install --upgrade pip wheel
"${VENV_DIR}/bin/pip" install -r "${AI_SERVICE}/requirements-no-cuda.txt"

if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
  cp "${PROJECT_ROOT}/config/ai/profiles/dev-cpu.env.example" "${PROJECT_ROOT}/.env"
  echo "已从 dev-cpu.env.example 生成 .env（请按需修改）"
else
  echo "已存在 .env，跳过生成"
fi

echo
echo "开发机启动命令："
echo "  cd ai-service-python"
echo "  .venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8001"
echo "验证：bash scripts/ai/verify-dev-cpu.sh"
