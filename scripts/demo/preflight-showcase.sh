#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command python3
require_command docker

SERVER_PORT="$(env_value URBAN_SAFE_SERVER_PORT || echo 8888)"
API_BASE="http://127.0.0.1:${SERVER_PORT}"
COMPOSE=(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}")
TMP_DIR="${RUN_DIR}/preflight"
mkdir -p "${TMP_DIR}"

fail_count=0
warn_count=0

pf_pass() { echo "[PASS] $*"; }
pf_warn() { echo "[WARN] $*"; warn_count=$((warn_count + 1)); }
pf_fail() { echo "[FAIL] $*" >&2; fail_count=$((fail_count + 1)); }
pf_auto() { echo "[AUTO] $*"; }
pf_diag() { echo "[DIAG] $*"; }

is_true() {
  case "${1,,}" in
    true|t|1|yes|y|on) return 0 ;;
    *) return 1 ;;
  esac
}

api_json() {
  local method="$1" url="$2" token="$3" body="${4:-}"
  if [[ -n "${body}" ]]; then
    curl --noproxy '*' -fsS -X "${method}" \
      -H "Authorization: Bearer ${token}" \
      -H 'Content-Type: application/json' \
      -d "${body}" "${url}"
  else
    curl --noproxy '*' -fsS -X "${method}" \
      -H "Authorization: Bearer ${token}" "${url}"
  fi
}

query_single() {
  local sql="$1"
  "${COMPOSE[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -At -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<<"${sql}"
}

load_review_workflow_row() {
  query_single "SELECT enabled::text || '|' || COALESCE(quality_status,'') || '|' || COALESCE(current_version,'') FROM ai.workflow_definition WHERE workflow_code='DIFY-REVIEW-ASSIST-001' LIMIT 1;"
}

load_golden_row() {
  "${COMPOSE[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -At -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL'
SELECT b.id,
       b.building_code,
       b.building_name,
       r.risk_level,
       r.risk_score,
       p.priority_level,
       p.priority_score,
       (
         SELECT ab.asset_id
         FROM core.inspection_record ir
         JOIN asset.asset_binding ab
           ON ab.business_type='INSPECTION_RECORD'
          AND ab.business_id=ir.id
          AND ab.deleted_at IS NULL
          AND ab.binding_role='PHOTO'
         JOIN asset.file_asset fa ON fa.id=ab.asset_id AND fa.deleted_at IS NULL AND fa.upload_status='AVAILABLE'
         WHERE ir.building_id=b.id AND ir.deleted_at IS NULL AND ir.status='COMPLETED'
         ORDER BY ir.inspected_at DESC, ir.id DESC
         LIMIT 1
       ) AS asset_id
FROM core.building b
JOIN core.risk_assessment r ON r.building_id=b.id AND r.status='CURRENT'
JOIN core.renewal_priority p
  ON p.building_id=b.id AND p.status='CURRENT' AND p.ranking_scope_key='ALL'
WHERE b.deleted_at IS NULL
  AND COALESCE(b.extra_attributes->>'showcaseGolden','false')='true'
  AND b.extra_attributes->>'showcaseGoldenSlot'='1'
LIMIT 1;
SQL
}

echo "========================================"
echo " 城安智序 · 比赛主演示一键预检"
echo "========================================"

printf '\n[1/6] 环境与云能力\n'
if bash "${PROJECT_ROOT}/scripts/dev/check-env-readiness.sh" --connectivity; then
  pf_pass "基础环境就绪检查通过"
else
  pf_fail "基础环境就绪检查失败"
fi

if ! is_true "$(env_value URBAN_SAFE_DIFY_ENABLED || true)"; then
  pf_fail "主演示要求 Dify Review Assist：URBAN_SAFE_DIFY_ENABLED 不是 true"
fi
if [[ -z "$(env_value URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY || true)" ]]; then
  pf_fail "主演示要求 Dify Review Assist 专用 API Key"
fi

if (( fail_count > 0 )); then
  echo "环境基础条件未满足，停止后续外部模型调用。" >&2
  exit 1
fi

printf '\n[2/6] 治理开关与黄金楼栋\n'
if ! "${COMPOSE[@]}" ps --status running --services | grep -qx postgresql; then
  pf_fail "PostgreSQL 未运行"
  exit 1
fi

prepare_log="${TMP_DIR}/prepare-showcase.log"
workflow_sql="SELECT COALESCE((SELECT boolean_value FROM ai.governance_setting WHERE setting_key='INTELLIGENT_WORKFLOW_ENABLED' LIMIT 1), TRUE);"
workflow_enabled="$(query_single "${workflow_sql}")"
if is_true "${workflow_enabled}"; then
  pf_pass "数据库智能工作流开关已启用"
else
  pf_auto "检测到 INTELLIGENT_WORKFLOW_ENABLED=false，正在执行比赛展示自动准备"
  if bash "${PROJECT_ROOT}/scripts/dev/prepare-showcase-golden-buildings.sh" "${ENV_FILE}" >"${prepare_log}" 2>&1; then
    workflow_enabled="$(query_single "${workflow_sql}")"
    if is_true "${workflow_enabled}"; then
      pf_pass "数据库智能工作流开关已自动开启"
    else
      pf_fail "展示准备脚本执行完成，但 INTELLIGENT_WORKFLOW_ENABLED 仍为关闭状态；日志：${prepare_log}"
      tail -n 40 "${prepare_log}" >&2 || true
      exit 1
    fi
  else
    pf_fail "自动准备比赛展示环境失败；日志：${prepare_log}"
    tail -n 40 "${prepare_log}" >&2 || true
    exit 1
  fi
fi

review_row="$(load_review_workflow_row)"
if [[ -z "${review_row}" ]]; then
  pf_fail "数据库未登记 DIFY-REVIEW-ASSIST-001；请确认 Flyway V31/V41 已执行并重启后端"
  exit 1
fi
IFS='|' read -r REVIEW_ENABLED REVIEW_QUALITY REVIEW_VERSION <<<"${review_row}"
if ! is_true "${REVIEW_ENABLED}"; then
  pf_auto "检测到 DIFY-REVIEW-ASSIST-001.enabled=false，正在执行比赛展示自动准备"
  if bash "${PROJECT_ROOT}/scripts/dev/prepare-showcase-golden-buildings.sh" "${ENV_FILE}" >"${prepare_log}" 2>&1; then
    review_row="$(load_review_workflow_row)"
    IFS='|' read -r REVIEW_ENABLED REVIEW_QUALITY REVIEW_VERSION <<<"${review_row}"
  else
    pf_fail "自动启用 Dify Review Assist 失败；日志：${prepare_log}"
    tail -n 40 "${prepare_log}" >&2 || true
    exit 1
  fi
fi
if ! is_true "${REVIEW_ENABLED}"; then
  pf_fail "DIFY-REVIEW-ASSIST-001 仍未启用；日志：${prepare_log}"
  exit 1
fi
pf_pass "Dify Review Assist 数据库工作流已启用（quality=${REVIEW_QUALITY:-UNKNOWN}，version=${REVIEW_VERSION:-UNKNOWN}）"

golden_row="$(load_golden_row)"
if [[ -z "${golden_row}" ]]; then
  pf_warn "未找到 golden slot 1，尝试按当前完整业务数据重新选择黄金楼栋"
  if bash "${PROJECT_ROOT}/scripts/dev/prepare-showcase-golden-buildings.sh" "${ENV_FILE}" >/dev/null; then
    golden_row="$(load_golden_row)"
  fi
fi
if [[ -z "${golden_row}" ]]; then
  pf_fail "无法准备 golden slot 1；请先补齐 CURRENT 评分、巡检记录和图片证据"
  exit 1
fi

IFS='|' read -r BUILDING_ID BUILDING_CODE BUILDING_NAME RISK_LEVEL RISK_SCORE PRIORITY_LEVEL PRIORITY_SCORE ASSET_ID <<<"${golden_row}"
if [[ -z "${BUILDING_ID}" || -z "${ASSET_ID}" || -z "${RISK_LEVEL}" || -z "${PRIORITY_LEVEL}" ]]; then
  pf_fail "黄金楼栋链路不完整：building/asset/risk/priority 存在空值"
  exit 1
fi
pf_pass "黄金楼栋：${BUILDING_NAME}（${BUILDING_CODE}）"
pf_pass "正式风险：${RISK_LEVEL} / ${RISK_SCORE}；更新优先级：${PRIORITY_LEVEL} / ${PRIORITY_SCORE}"
pf_pass "巡检图片 assetId 已就绪：${ASSET_ID}"

printf '\n[3/6] 专家账号登录\n'
login_response="$(curl --noproxy '*' -fsS -H 'Content-Type: application/json' \
  -d '{"username":"demo_expert","password":"UrbanSafe@123"}' \
  "${API_BASE}/api/v1/auth/login")" || {
    pf_fail "demo_expert 登录失败"
    exit 1
  }
TOKEN="$(LOGIN_RESPONSE="${login_response}" python3 - <<'PY'
import json, os
payload=json.loads(os.environ['LOGIN_RESPONSE'])
print((payload.get('data') or {}).get('accessToken') or '')
PY
)"
if [[ -z "${TOKEN}" ]]; then
  pf_fail "登录响应缺少 accessToken"
  exit 1
fi
pf_pass "demo_expert 登录成功（Token 未输出）"

printf '\n[4/6] 提交异步综合研判\n'
request_body="$(BUILDING_ID="${BUILDING_ID}" ASSET_ID="${ASSET_ID}" python3 - <<'PY'
import json, os
print(json.dumps({
  'businessType':'AI_INFERENCE',
  'businessId':os.environ['BUILDING_ID'],
  'question':'比赛链路预检：请必须调用楼栋档案、巡检证据、风险评估、更新优先级、实时视觉分析和 Dify 复核辅助工具，再按固定八段结构形成综合研判。不得修改正式评分。',
  'context':{'buildingId':os.environ['BUILDING_ID'],'assetId':os.environ['ASSET_ID']}
}, ensure_ascii=False))
PY
)"
submission="$(api_json POST "${API_BASE}/api/v1/ai-intelligent-analysis/tasks" "${TOKEN}" "${request_body}")" || {
  pf_fail "综合研判任务提交失败"
  exit 1
}
TASK_ID="$(SUBMISSION="${submission}" python3 - <<'PY'
import json, os
p=json.loads(os.environ['SUBMISSION'])
print((p.get('data') or {}).get('taskId') or '')
PY
)"
if [[ -z "${TASK_ID}" ]]; then
  pf_fail "综合研判提交响应缺少 taskId"
  exit 1
fi
pf_pass "任务已提交：${TASK_ID}"

printf '\n[5/6] 等待模型与工具链完成\n'
result_file="${TMP_DIR}/analysis-${TASK_ID}.json"
final_status=""
for _ in $(seq 1 150); do
  response="$(api_json GET "${API_BASE}/api/v1/ai-intelligent-analysis/tasks/${TASK_ID}" "${TOKEN}")" || true
  if [[ -z "${response}" ]]; then
    sleep 2
    continue
  fi
  printf '%s' "${response}" > "${result_file}"
  final_status="$(TASK_RESPONSE="${response}" python3 - <<'PY'
import json, os
try:
    p=json.loads(os.environ['TASK_RESPONSE'])
    print((p.get('data') or {}).get('status') or '')
except Exception:
    print('')
PY
)"
  case "${final_status}" in
    SUCCEEDED|FAILED|REJECTED|CANCELLED) break ;;
  esac
  sleep 2
done

if [[ "${final_status}" != "SUCCEEDED" ]]; then
  pf_fail "综合研判未成功完成，最终状态=${final_status:-UNKNOWN}；详情保存在 ${result_file}"
else
  pf_pass "综合研判后台任务 SUCCEEDED"
fi

printf '\n[6/6] 核心工具闭环\n'
if [[ -s "${result_file}" ]]; then
  TOOL_REPORT="$(RESULT_FILE="${result_file}" python3 - <<'PY'
import json, os
p=json.load(open(os.environ['RESULT_FILE'], encoding='utf-8'))
data=p.get('data') or {}
result=data.get('result') or {}
steps=result.get('steps') or []
required=['BuildingOverviewTool','InspectionEvidenceTool','RiskAssessmentTool','RenewalPriorityTool','VisionAnalysisTool','DifyReviewAssistTool']
by={s.get('toolName'):s for s in steps if s.get('type')=='TOOL'}
for name in required:
    s=by.get(name)
    status=(s or {}).get('status') or 'NOT_CALLED'
    error=(s or {}).get('errorCode') or ''
    print(f'{name}|{status}|{error}')
PY
)"
  while IFS='|' read -r tool status error; do
    [[ -n "${tool}" ]] || continue
    if [[ "${status}" == "SUCCEEDED" ]]; then
      pf_pass "${tool}：SUCCEEDED"
    else
      pf_fail "${tool}：${status}${error:+ / ${error}}"
      if [[ "${tool}" == "DifyReviewAssistTool" && "${error}" == "AI_PROVIDER_DISABLED" ]]; then
        current_review_row="$(load_review_workflow_row)"
        IFS='|' read -r current_review_enabled current_review_quality current_review_version <<<"${current_review_row}"
        if is_true "${current_review_enabled}" && is_true "$(env_value URBAN_SAFE_DIFY_ENABLED || true)"; then
          pf_diag "数据库 DIFY-REVIEW-ASSIST-001 已启用，当前 .env 也为 URBAN_SAFE_DIFY_ENABLED=true。"
          pf_diag "DifyApiClient 只有‘Spring Boot 运行时 Dify 开关=false’或‘数据库工作流 enabled=false’才返回 AI_PROVIDER_DISABLED；当前数据库已排除，因此请重启 Spring Boot，使启动时配置重新绑定后再跑预检。"
        fi
      fi
    fi
  done <<<"${TOOL_REPORT}"
else
  pf_fail "没有生成综合研判结果文件"
fi

echo
echo "========================================"
echo " 预检结果：FAIL=${fail_count} WARN=${warn_count}"
echo "========================================"
echo "黄金楼栋：${BUILDING_NAME}（${BUILDING_CODE}）"
echo "风险/优先级：${RISK_LEVEL} / ${PRIORITY_LEVEL}"
echo "任务记录：${result_file}"

if (( fail_count > 0 )); then
  echo "主演示链路存在阻断项，请先修复 FAIL 后再上台演示。" >&2
  exit 1
fi

pf_pass "主演示核心链路预检通过"
