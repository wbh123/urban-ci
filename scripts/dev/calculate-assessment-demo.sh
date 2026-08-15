#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少 .env 文件：${env_file}" >&2
  exit 1
fi
for command in curl python3; do
  if ! command -v "${command}" >/dev/null 2>&1; then
    echo "未找到 ${command} 命令，无法生成评分展示数据。" >&2
    exit 1
  fi
done

read_dotenv_value() {
  local key="$1"
  local fallback="${2:-}"
  ENV_FILE="${env_file}" ENV_KEY="${key}" ENV_FALLBACK="${fallback}" python3 <<'PY_ENV'
import os
from pathlib import Path

path = Path(os.environ["ENV_FILE"])
key = os.environ["ENV_KEY"]
fallback = os.environ.get("ENV_FALLBACK", "")
value = None

for raw_line in path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#"):
        continue
    if line.startswith("export "):
        line = line[7:].lstrip()
    if "=" not in line:
        continue
    name, raw_value = line.split("=", 1)
    if name.strip() != key:
        continue
    candidate = raw_value.strip()
    if len(candidate) >= 2 and candidate[0] == candidate[-1] and candidate[0] in {"'", '"'}:
        candidate = candidate[1:-1]
    value = candidate
    break

print(value if value not in (None, "") else fallback)
PY_ENV
}

server_port="${URBAN_SAFE_SERVER_PORT:-$(read_dotenv_value URBAN_SAFE_SERVER_PORT '8888')}"
configured_api_base="${URBAN_SAFE_API_BASE_URL:-$(read_dotenv_value URBAN_SAFE_API_BASE_URL '')}"
compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

# 本地 WSL/开发环境可能配置 http_proxy/https_proxy。所有 localhost/127.0.0.1
# Spring Boot 调用必须显式绕过代理，否则即使端口已经监听，也可能被代理劫持。
local_curl=(curl --noproxy '*')

is_backend_ready() {
  local candidate="$1"
  local payload
  payload="$("${local_curl[@]}" -fsS --connect-timeout 1 --max-time 3 "${candidate}/api/v1/system/health" 2>/dev/null || true)"
  [[ -n "${payload}" ]] || return 1

  HEALTH_PAYLOAD="${payload}" python3 <<'PY_HEALTH' >/dev/null 2>&1
import json
import os

try:
    payload = json.loads(os.environ["HEALTH_PAYLOAD"])
except Exception:
    raise SystemExit(1)

data = payload.get("data") or {}
if payload.get("success") is not True:
    raise SystemExit(1)
if data.get("service") != "urban-safe-priority-server":
    raise SystemExit(1)
if data.get("status") != "UP":
    raise SystemExit(1)
PY_HEALTH
}

diagnose_candidate() {
  local candidate="$1"
  local tmp_file
  local http_code
  tmp_file="$(mktemp)"
  http_code="$("${local_curl[@]}" -sS --connect-timeout 2 --max-time 4 \
    -o "${tmp_file}" -w '%{http_code}' \
    "${candidate}/api/v1/system/health" 2>/dev/null || true)"
  [[ -n "${http_code}" ]] || http_code="000"
  echo "  ${candidate}/api/v1/system/health -> HTTP ${http_code}" >&2
  if [[ -s "${tmp_file}" ]]; then
    RESPONSE_FILE="${tmp_file}" python3 <<'PY_RESPONSE' >&2
import os
from pathlib import Path

text = Path(os.environ["RESPONSE_FILE"]).read_text(encoding="utf-8", errors="replace")
text = " ".join(text.split())
if len(text) > 240:
    text = text[:240] + "…"
print(f"    响应：{text}")
PY_RESPONSE
  fi
  rm -f "${tmp_file}"
}

resolve_api_base() {
  local -a candidates=()
  local candidate

  if [[ -n "${configured_api_base}" ]]; then
    candidates+=("${configured_api_base%/}")
  fi

  # WSL/Windows 联调时 localhost 与 127.0.0.1 的转发行为可能不同，因此两者都探测。
  candidates+=(
    "http://localhost:${server_port}"
    "http://127.0.0.1:${server_port}"
  )

  # 兼容本地 IDE/历史配置使用的常见 Spring Boot 端口。
  for port in 8888 8080 8081 8090; do
    candidates+=("http://localhost:${port}" "http://127.0.0.1:${port}")
  done

  local seen="|"
  for candidate in "${candidates[@]}"; do
    [[ "${seen}" == *"|${candidate}|"* ]] && continue
    seen+="${candidate}|"
    if is_backend_ready "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
  done

  echo "无法识别已监听的 Spring Boot 后端。" >&2
  echo "已绕过系统 HTTP/HTTPS 代理并探测以下地址：" >&2
  seen="|"
  for candidate in "${candidates[@]}"; do
    [[ "${seen}" == *"|${candidate}|"* ]] && continue
    seen+="${candidate}|"
    diagnose_candidate "${candidate}"
  done
  echo "当前 .env 中 URBAN_SAFE_SERVER_PORT=${server_port}" >&2
  if [[ -n "${configured_api_base}" ]]; then
    echo "当前显式 URBAN_SAFE_API_BASE_URL=${configured_api_base}" >&2
  fi
  if command -v ss >/dev/null 2>&1; then
    echo "当前 8888 监听进程：" >&2
    ss -ltnp 2>/dev/null | grep -E '(:8888[[:space:]])' >&2 || true
  fi
  if command -v ps >/dev/null 2>&1; then
    echo "当前 Java 进程（截取）：" >&2
    ps -ef 2>/dev/null | grep '[j]ava' | head -n 8 >&2 || true
  fi
  echo "如果这里显示 HTTP 404/401/500，请把上面的状态码和响应摘要反馈回来。" >&2
  return 1
}

api_base="$(resolve_api_base)"
echo "已连接 Spring Boot 后端：${api_base}"

existing_current="$(${compose[@]} exec -T postgresql sh -eu -c \
  'psql -At -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_COUNT'
SELECT count(*)
FROM core.risk_assessment r
JOIN core.rule_version rv ON rv.id=r.rule_version_id
JOIN core.building b ON b.id=r.building_id
JOIN core.community c ON c.id=b.community_id
WHERE r.status='CURRENT'
  AND r.engine_version='phase4-rule-engine-v1'
  AND rv.rule_type='RISK'
  AND rv.status='ACTIVE'
  AND rv.deleted_at IS NULL
  AND c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
  AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02');
SQL_COUNT
)"

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

mapfile -t buildings < <(${compose[@]} exec -T postgresql sh -eu -c \
  'psql -At -F "," -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_BUILDINGS'
SELECT b.building_code, b.id
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
  AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02')
  AND b.deleted_at IS NULL
ORDER BY CASE b.building_code
  WHEN 'B-01' THEN 1 WHEN 'A-03' THEN 2 WHEN 'B-02' THEN 3 WHEN 'A-02' THEN 4 ELSE 5 END;
SQL_BUILDINGS
)

if [[ "${#buildings[@]}" -ne 5 ]]; then
  echo "预期 5 栋演示楼栋，实际 ${#buildings[@]} 栋。" >&2
  exit 1
fi

all_reused=true
for row in "${buildings[@]}"; do
  code="${row%%,*}"
  building_id="${row#*,}"
  response="$("${local_curl[@]}" -fsS -H "Authorization: Bearer ${access_token}" \
    -H 'Content-Type: application/json' \
    -d '{"force":false,"rankingScopes":["COMMUNITY","ALL"]}' \
    "${api_base}/api/v1/assessments/buildings/${building_id}/calculate")"
  reused="$(CALC_RESPONSE="${response}" python3 - <<'PY_CALC'
import json, os
payload = json.loads(os.environ['CALC_RESPONSE'])
if not payload.get('success'):
    raise SystemExit('接口返回 success=false')
print(str(payload.get('data', {}).get('reused', False)).lower())
PY_CALC
)"
  printf '计算 %-4s buildingId=%s reused=%s\n' "${code}" "${building_id}" "${reused}"
  if [[ "${reused}" != "true" ]]; then
    all_reused=false
  fi
done

if [[ "${existing_current}" -ge 5 && "${all_reused}" != "true" ]]; then
  echo "已有当前评分时，非 force 计算应全部复用，但本次存在 reused=false。" >&2
  exit 1
fi
