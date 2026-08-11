#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少 .env 文件：${env_file}" >&2
  exit 1
fi
for command in curl python3 docker; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "缺少命令：${command}" >&2
    exit 1
  fi
done

read_dotenv_value() {
  local key="$1"
  local fallback="${2:-}"
  ENV_FILE="${env_file}" ENV_KEY="${key}" ENV_FALLBACK="${fallback}" python3 <<'PY_ENV'
import os
from pathlib import Path
path = Path(os.environ['ENV_FILE'])
key = os.environ['ENV_KEY']
fallback = os.environ.get('ENV_FALLBACK', '')
for raw in path.read_text(encoding='utf-8').splitlines():
    line = raw.strip()
    if not line or line.startswith('#') or '=' not in line:
        continue
    if line.startswith('export '):
        line = line[7:].lstrip()
    name, value = line.split('=', 1)
    if name.strip() != key:
        continue
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {'"', "'"}:
        value = value[1:-1]
    print(value or fallback)
    break
else:
    print(fallback)
PY_ENV
}

server_port="${URBAN_SAFE_SERVER_PORT:-$(read_dotenv_value URBAN_SAFE_SERVER_PORT '8888')}"
configured_api_base="${URBAN_SAFE_API_BASE_URL:-$(read_dotenv_value URBAN_SAFE_API_BASE_URL '')}"
local_curl=(curl --noproxy '*')
compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

resolve_api_base() {
  local candidate payload
  local -a candidates=()
  [[ -n "${configured_api_base}" ]] && candidates+=("${configured_api_base%/}")
  candidates+=("http://localhost:${server_port}" "http://127.0.0.1:${server_port}")
  for candidate in "${candidates[@]}"; do
    payload="$("${local_curl[@]}" -fsS --connect-timeout 1 --max-time 4 \
      "${candidate}/api/v1/system/health" 2>/dev/null || true)"
    if HEALTH_PAYLOAD="${payload}" python3 - <<'PY_HEALTH' >/dev/null 2>&1
import json, os
try:
    payload = json.loads(os.environ.get('HEALTH_PAYLOAD', ''))
except Exception:
    raise SystemExit(1)
data = payload.get('data') or {}
raise SystemExit(0 if payload.get('success') is True and data.get('service') == 'urban-safe-priority-server' and data.get('status') == 'UP' else 1)
PY_HEALTH
    then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  echo "无法连接 Spring Boot：已检查 localhost/127.0.0.1:${server_port}" >&2
  return 1
}

api_base="$(resolve_api_base)"
echo "已连接 Spring Boot：${api_base}"

login_response="$("${local_curl[@]}" -fsS -H 'Content-Type: application/json' \
  -d '{"username":"demo_admin","password":"UrbanSafe@123"}' \
  "${api_base}/api/v1/auth/login")"
access_token="$(LOGIN_RESPONSE="${login_response}" python3 - <<'PY_LOGIN'
import json, os
payload = json.loads(os.environ['LOGIN_RESPONSE'])
print(payload.get('data', {}).get('accessToken', ''))
PY_LOGIN
)"
if [[ -z "${access_token}" ]]; then
  echo "登录响应中没有 accessToken。" >&2
  exit 1
fi

mapfile -t buildings < <("${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -At -F "," -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_BUILDINGS'
SELECT b.building_code, b.id
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE b.deleted_at IS NULL
  AND COALESCE(b.extra_attributes->>'showcaseGenerated','false')='true'
ORDER BY c.community_name, b.building_code;
SQL_BUILDINGS
)

if [[ "${#buildings[@]}" -eq 0 ]]; then
  echo "没有找到城市样例楼栋，请先执行 generate-showcase-data.sh。" >&2
  exit 1
fi

limit="${SHOWCASE_ASSESSMENT_LIMIT:-0}"
if [[ "${limit}" =~ ^[0-9]+$ ]] && (( limit > 0 && limit < ${#buildings[@]} )); then
  buildings=("${buildings[@]:0:limit}")
fi

min_success_rate="${SHOWCASE_MIN_ASSESSMENT_SUCCESS_RATE:-90}"
if ! [[ "${min_success_rate}" =~ ^[0-9]+$ ]] || (( min_success_rate < 0 || min_success_rate > 100 )); then
  echo "SHOWCASE_MIN_ASSESSMENT_SUCCESS_RATE 必须是 0-100 的整数，当前值：${min_success_rate}" >&2
  exit 1
fi

success=0
failed=0
index=0
for row in "${buildings[@]}"; do
  index=$((index + 1))
  code="${row%%,*}"
  building_id="${row#*,}"
  if response="$("${local_curl[@]}" -fsS -H "Authorization: Bearer ${access_token}" \
      -H 'Content-Type: application/json' \
      -d '{"force":false,"rankingScopes":["COMMUNITY","ALL"]}' \
      "${api_base}/api/v1/assessments/buildings/${building_id}/calculate" 2>/dev/null)"; then
    if CALC_RESPONSE="${response}" python3 - <<'PY_CALC' >/dev/null 2>&1
import json, os
payload = json.loads(os.environ['CALC_RESPONSE'])
raise SystemExit(0 if payload.get('success') is True else 1)
PY_CALC
    then
      success=$((success + 1))
    else
      failed=$((failed + 1))
    fi
  else
    failed=$((failed + 1))
  fi
  if (( index % 20 == 0 || index == ${#buildings[@]} )); then
    echo "风险评分进度：${index}/${#buildings[@]}，成功=${success}，失败=${failed}"
  fi
done

total="${#buildings[@]}"
success_rate=$(( success * 100 / total ))
echo "城市样例楼栋风险评分完成：成功=${success}，失败=${failed}，总计=${total}，成功率=${success_rate}%"

if (( success_rate < min_success_rate )); then
  echo "评分成功率低于展示数据最低要求：实际=${success_rate}%，最低=${min_success_rate}%；请先排查失败楼栋后再继续生成展示数据。" >&2
  exit 2
fi

if (( failed > 0 )); then
  echo "存在 ${failed} 栋评分失败，但成功率已达到展示数据最低要求 ${min_success_rate}%；可后续单独排查。" >&2
fi
