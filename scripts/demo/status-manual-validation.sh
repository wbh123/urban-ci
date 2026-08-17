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

has_env() {
  [[ -n "$(env_value "$1" || true)" ]]
}

is_true() {
  case "${1,,}" in
    true|1|yes|on) return 0 ;;
    *) return 1 ;;
  esac
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

echo "\n[云端 AI 主演示能力，仅显示状态，不输出密钥]"
if is_true "$(env_value URBAN_SAFE_DIFY_ENABLED || true)"; then
  echo "[READY] Dify Provider"
else
  echo "[OFF  ] Dify Provider"
fi
if has_env URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY; then
  echo "[READY] Dify Review Assist · 主演示依赖"
else
  echo "[MISS ] Dify Review Assist · 缺少专用 API Key"
fi
if has_env URBAN_SAFE_DIFY_REPORT_DRAFT_API_KEY; then
  echo "[HOLD ] Dify Report Draft · 已配置但当前综合研判不暴露"
else
  echo "[SKIP ] Dify Report Draft · 主演示不依赖"
fi
if has_env URBAN_SAFE_SPRING_AI_API_KEY; then
  echo "[READY] Spring AI / DeepSeek Key"
else
  echo "[MISS ] Spring AI / DeepSeek Key"
fi

echo "\n[黄金演示楼栋]"
if docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" ps --status running --services 2>/dev/null | grep -qx postgresql; then
  golden_count="$(
    docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T postgresql sh -eu -c \
      'psql -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>/dev/null <<'SQL' || echo 0
SELECT count(*)
FROM core.building
WHERE deleted_at IS NULL
  AND COALESCE(extra_attributes->>'showcaseGolden','false')='true';
SQL
  )"
  golden_count="${golden_count//$'\r'/}"
  golden_count="${golden_count//$'\n'/}"
  if [[ "${golden_count}" =~ ^[0-9]+$ ]] && (( golden_count >= 3 )); then
    echo "[READY] 黄金楼栋 ${golden_count} 栋"
  else
    echo "[MISS ] 黄金楼栋不足 3 栋；执行 bash scripts/dev/prepare-showcase-golden-buildings.sh"
  fi
else
  echo "[SKIP ] PostgreSQL 未运行，无法检查黄金楼栋"
fi

echo "\n日志目录：${LOG_DIR}"
