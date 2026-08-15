#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

AI_PORT="$(env_value URBAN_SAFE_AI_SERVICE_PORT || echo 8001)"
SERVER_PORT="$(env_value URBAN_SAFE_SERVER_PORT || echo 8888)"
FRONT_PORT=5173
AI_PYTHON="$(resolve_ai_python)"

status_http() {
  local name="$1" url="$2"
  if curl -fsS "${url}" >/dev/null 2>&1; then
    echo "[READY] ${name}  ${url}"
  else
    echo "[DOWN ] ${name}  ${url}"
  fi
}

echo "========================================"
echo " 城安智序手动验证环境状态"
echo "========================================"

echo "\n[Docker 基础设施]"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps postgresql minio 2>/dev/null || true

echo "\n[宿主机进程]"
for name in fastapi backend frontend; do
  if process_alive "${name}"; then
    echo "[RUN  ] ${name} PID=$(cat "$(pid_file "${name}")") log=$(log_file "${name}")"
  else
    echo "[STOP ] ${name}"
  fi
done

echo "\n[HTTP]"
status_http FastAPI-health "http://127.0.0.1:${AI_PORT}/internal/api/v1/ai/health"
status_http FastAPI-ready "http://127.0.0.1:${AI_PORT}/internal/api/v1/ai/ready"
status_http Spring-Boot "http://127.0.0.1:${SERVER_PORT}/actuator/health"
status_http Vue "http://127.0.0.1:${FRONT_PORT}/"

if curl -fsS "http://127.0.0.1:${AI_PORT}/internal/api/v1/ai/models/AI-VISION-LOCAL-001" >/tmp/urban-safe-model-status.json 2>/dev/null; then
  echo "\n[REAL 模型]"
  "${AI_PYTHON}" - <<'PY'
import json
m=json.load(open('/tmp/urban-safe-model-status.json'))
print('modelId          =', m.get('modelId'))
print('mode             =', m.get('mode'))
print('status           =', m.get('status'))
print('ready            =', m.get('ready'))
print('executionProvider=', m.get('executionProvider'))
PY
fi

echo "\n[云端 AI 配置，仅显示是否配置，不输出密钥]"
for key in URBAN_SAFE_SPRING_AI_API_KEY URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY URBAN_SAFE_DIFY_REPORT_DRAFT_API_KEY; do
  value="$(env_value "${key}" || true)"
  if [[ -n "${value}" ]]; then
    echo "[CONFIGURED] ${key}"
  else
    echo "[UNCONFIG  ] ${key}"
  fi
done

echo "\n日志目录：${LOG_DIR}"
