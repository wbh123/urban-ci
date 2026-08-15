#!/usr/bin/env bash

# Profile A 验证：Python 依赖可安装、FastAPI 启动、/ready、MOCK 推理、
# CPU 图片适用性、无 CUDA 时不谎报 REAL READY。
# 注意：不使用 with-cuda-env.sh（该脚本要求 CUDA）。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AI_SERVICE="${PROJECT_ROOT}/ai-service-python"

DEV_PYTHON="${DEV_PYTHON:-${AI_SERVICE}/.venv/bin/python}"
if [[ ! -x "${DEV_PYTHON}" ]]; then
  DEV_PYTHON="$(command -v python3 || true)"
fi
if [[ -z "${DEV_PYTHON}" ]]; then
  echo "未找到 Python（请先运行 setup-dev-cpu.sh）" >&2
  exit 1
fi

echo "== [1/2] 离线安全测试（显式 MOCK，不加载真实模型）=="
cd "${AI_SERVICE}"
"${DEV_PYTHON}" -m pytest tests/test_main.py tests/test_mock_determinism.py -q

echo "== [2/2] FastAPI 冒烟（dev-cpu profile 环境变量）=="
export URBAN_SAFE_AI_DEFAULT_MODE=MOCK
export URBAN_SAFE_AI_MODEL_CATALOG_PATH=missing-catalog.json
export URBAN_SAFE_AI_REAL_MODEL_STATUS=UNAVAILABLE
export URBAN_SAFE_AI_APPLICABILITY_ENABLED=false

PORT=18081
"${DEV_PYTHON}" -m uvicorn app.main:app --host 127.0.0.1 --port "${PORT}" &
SERVER_PID=$!
trap 'kill "${SERVER_PID}" 2>/dev/null || true' EXIT

READY_FILE=$(mktemp)
for _ in $(seq 1 30); do
  if curl -fsS "http://127.0.0.1:${PORT}/internal/api/v1/ai/ready" > "${READY_FILE}" 2>/dev/null; then
    break
  fi
  sleep 1
done
"${DEV_PYTHON}" - "${READY_FILE}" <<'PY'
import json, pathlib, sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert payload["realModelCount"] == 0, f"开发机不得宣称 REAL 模型就绪：{payload}"
print("READY 正确：realModelCount=0，未谎报 REAL READY")
PY

curl -fsS "http://127.0.0.1:${PORT}/internal/api/v1/ai/models/current" \
  | "${DEV_PYTHON}" -c 'import json,sys; d=json.load(sys.stdin); assert d["mode"]=="MOCK", d; print("当前模型：", d["modelId"], d["mode"])'

echo "Profile A 验证通过：MOCK 正常，无 CUDA 不谎报 REAL READY。"
