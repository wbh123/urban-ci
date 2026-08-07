#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少 .env 文件：${env_file}" >&2
  exit 1
fi
if ! command -v curl >/dev/null 2>&1; then
  echo "未找到 curl 命令，无法调用正式评分接口。" >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
. "${env_file}"
set +a

api_base="${URBAN_SAFE_API_BASE_URL:-http://127.0.0.1:${URBAN_SAFE_SERVER_PORT:-8888}}"
compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

if ! curl -fsS "${api_base}/api/v1/system/health" >/dev/null; then
  echo "Spring Boot 后端未就绪：${api_base}" >&2
  echo "请先启动后端，例如：mamba run -n urban mvn -f backend-java/pom.xml -pl starter spring-boot:run" >&2
  exit 1
fi

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

login_response="$(curl -fsS -H 'Content-Type: application/json' \
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
  response="$(curl -fsS -H "Authorization: Bearer ${access_token}" \
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
