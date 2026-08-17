#!/usr/bin/env bash
set -uo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${repository_root}/.env"
connectivity=false

for arg in "$@"; do
  case "${arg}" in
    --connectivity) connectivity=true ;;
    -h|--help)
      cat <<'EOF'
用法：
  bash scripts/dev/check-env-readiness.sh
  bash scripts/dev/check-env-readiness.sh --connectivity

默认只检查 .env 配置语义和本机报告字体，不打印任何密钥。
--connectivity 额外执行只读连通性探测：Spring Boot、FastAPI、MinIO、PostgreSQL、Dify Review Assist、DeepSeek。
EOF
      exit 0
      ;;
    *) echo "[FAIL] 未知参数：${arg}" >&2; exit 2 ;;
  esac
done

if [[ ! -f "${env_file}" ]]; then
  echo "[FAIL] 缺少根目录 .env：${env_file}" >&2
  echo "       请先执行：cp .env.example .env" >&2
  exit 1
fi

declare -A env_values=()
while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
  line="${raw_line%$'\r'}"
  trimmed="${line#"${line%%[![:space:]]*}"}"
  [[ -z "${trimmed}" || "${trimmed}" == \#* ]] && continue
  [[ "${line}" == *=* ]] || continue
  key="${line%%=*}"
  value="${line#*=}"
  key="${key//[[:space:]]/}"
  [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  env_values["${key}"]="${value}"
done < "${env_file}"

pass_count=0
warn_count=0
fail_count=0

pass() { echo "[PASS] $*"; pass_count=$((pass_count + 1)); }
warn() { echo "[WARN] $*"; warn_count=$((warn_count + 1)); }
fail() { echo "[FAIL] $*"; fail_count=$((fail_count + 1)); }
info() { echo "[INFO] $*"; }

value_of() {
  local key="$1"
  printf '%s' "${env_values[${key}]-}"
}

has_value() {
  local value
  value="$(value_of "$1")"
  [[ -n "${value}" && "${value}" != *"请替换"* ]]
}

is_true() {
  case "${1,,}" in
    true|1|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

require_value() {
  local key="$1"
  local label="$2"
  if has_value "${key}"; then
    pass "${label}：已配置"
  else
    fail "${label}：缺少 ${key}"
  fi
}

check_tcp() {
  local host="$1"
  local port="$2"
  python3 - "${host}" "${port}" <<'PY' >/dev/null 2>&1
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(1.5)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

http_status() {
  local url="$1"
  shift
  curl -sS --connect-timeout 3 --max-time 8 -o /dev/null -w '%{http_code}' "$@" "${url}" 2>/dev/null || printf '000'
}

echo "========================================"
echo " 城安智序 .env 配置与运行就绪检测"
echo "========================================"
echo "配置文件：${env_file}"
echo

info "1/7 基础配置"
if bash "${repository_root}/scripts/dev/validate-env.sh" >/dev/null 2>&1; then
  pass "基础 .env 格式、数据库、MinIO、地图必填项校验通过"
else
  fail "基础 .env 校验未通过；请先运行 bash scripts/dev/validate-env.sh 查看具体缺失项"
fi

require_value URBAN_SAFE_DB_HOST "PostgreSQL 主机"
require_value URBAN_SAFE_DB_PORT "PostgreSQL 端口"
require_value URBAN_SAFE_MINIO_HOST "MinIO 主机"
require_value URBAN_SAFE_MINIO_API_PORT "MinIO API 端口"
require_value URBAN_SAFE_MINIO_REPORTS_BUCKET "报告存储桶"

echo
info "2/7 高德地图"
map_enabled="$(value_of URBAN_SAFE_MAP_ENABLED)"
if is_true "${map_enabled}"; then
  pass "地图总开关：已启用"
  require_value URBAN_SAFE_AMAP_JS_API_KEY "高德 JS API Key"
  require_value URBAN_SAFE_AMAP_SECURITY_JS_CODE "高德 Security JS Code"
  if has_value URBAN_SAFE_AMAP_WEB_SERVICE_KEY; then
    pass "高德 Web Service Key：已配置"
  else
    if is_true "$(value_of URBAN_SAFE_AMAP_BOUNDARY_CANDIDATE_ENABLED)"; then
      warn "候选边界已启用，但 URBAN_SAFE_AMAP_WEB_SERVICE_KEY 为空；地图可显示，候选边界查询可能不可用"
    else
      warn "高德 Web Service Key 未配置；不影响基础 JS 地图，但服务端地理查询能力受限"
    fi
  fi
else
  warn "地图总开关未启用：URBAN_SAFE_MAP_ENABLED=${map_enabled:-<未配置>}"
fi

echo
info "3/7 FastAPI 本地视觉"
require_value URBAN_SAFE_AI_SERVICE_BASE_URL "FastAPI 地址"
if is_true "$(value_of URBAN_SAFE_AI_EXECUTION_ENABLED)"; then
  pass "AI 异步执行器：已启用"
else
  warn "AI 异步执行器未启用；上传图片后不会自动执行 ACCURACY 任务"
fi

vision_provider="$(value_of URBAN_SAFE_AI_DEFAULT_VISION_PROVIDER)"
if [[ "${vision_provider^^}" == "FAST_API" ]]; then
  pass "默认视觉提供者：FAST_API"
else
  warn "默认视觉提供者不是 FAST_API：${vision_provider:-<未配置>}"
fi

echo
info "4/7 Dify 工作流"
dify_enabled="$(value_of URBAN_SAFE_DIFY_ENABLED)"
dify_base="$(value_of URBAN_SAFE_DIFY_BASE_URL)"
review_assist_key="$(value_of URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY)"
report_draft_key="$(value_of URBAN_SAFE_DIFY_REPORT_DRAFT_API_KEY)"
dify_key_names=(
  URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY
  URBAN_SAFE_DIFY_REPORT_DRAFT_API_KEY
  URBAN_SAFE_DIFY_KNOWLEDGE_QA_API_KEY
  URBAN_SAFE_DIFY_IMAGE_ANALYSIS_API_KEY
  URBAN_SAFE_DIFY_API_KEY
)
dify_configured_count=0
for key_name in "${dify_key_names[@]}"; do
  if has_value "${key_name}"; then
    dify_configured_count=$((dify_configured_count + 1))
  fi
done

if (( dify_configured_count > 0 )); then
  pass "Dify 工作流密钥：检测到 ${dify_configured_count} 组已配置（密钥内容已隐藏）"
else
  warn "Dify 工作流密钥：未检测到已配置工作流"
fi

if is_true "${dify_enabled}"; then
  pass "Dify Provider：已启用"
  [[ -n "${dify_base}" ]] && pass "Dify Base URL：已配置" || fail "Dify 已启用但 URBAN_SAFE_DIFY_BASE_URL 为空"
  if has_value URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY; then
    pass "Dify Review Assist：主演示工作流 Key 已配置"
  else
    fail "Dify 已启用但 Review Assist 未配置；缺少 URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY"
  fi
  if has_value URBAN_SAFE_DIFY_REPORT_DRAFT_API_KEY; then
    warn "Dify Report Draft Key 已配置，但当前主演示综合研判暂不暴露该工作流，等待契约单独收口"
  else
    warn "Dify Report Draft 未配置；当前主演示不依赖该能力"
  fi
else
  if (( dify_configured_count > 0 )); then
    fail "Dify 已配置密钥但 Provider 仍被禁用；请设置 URBAN_SAFE_DIFY_ENABLED=true"
  else
    warn "Dify Provider 未启用"
  fi
fi

echo
info "5/7 Spring AI Chat / DeepSeek"
spring_ai_enabled="$(value_of URBAN_SAFE_SPRING_AI_PROVIDER_ENABLED)"
spring_ai_key="$(value_of URBAN_SAFE_SPRING_AI_API_KEY)"
spring_ai_chat="$(value_of URBAN_SAFE_SPRING_AI_MODEL_CHAT)"
spring_ai_model="$(value_of URBAN_SAFE_SPRING_AI_MODEL)"
spring_ai_base="$(value_of URBAN_SAFE_SPRING_AI_BASE_URL)"

if [[ -n "${spring_ai_key}" ]]; then
  pass "Spring AI / DeepSeek API Key：已配置（内容已隐藏）"
else
  warn "Spring AI / DeepSeek API Key 未配置"
fi

if is_true "${spring_ai_enabled}"; then
  pass "Spring AI Provider：已启用"
else
  if [[ -n "${spring_ai_key}" ]]; then
    fail "Spring AI Key 已配置但 Provider 仍被禁用；请设置 URBAN_SAFE_SPRING_AI_PROVIDER_ENABLED=true"
  else
    warn "Spring AI Provider 未启用"
  fi
fi

if [[ "${spring_ai_chat,,}" == "openai" ]]; then
  pass "Spring AI Chat 自动配置：openai（DeepSeek OpenAI 兼容协议）"
else
  if [[ -n "${spring_ai_key}" ]] || is_true "${spring_ai_enabled}"; then
    fail "Spring AI Chat 未激活；请设置 URBAN_SAFE_SPRING_AI_MODEL_CHAT=openai，否则不会创建 ChatClient"
  else
    warn "Spring AI Chat 当前为 ${spring_ai_chat:-none}"
  fi
fi

[[ -n "${spring_ai_model}" ]] && pass "Spring AI 模型名：已配置" || fail "缺少 URBAN_SAFE_SPRING_AI_MODEL"
[[ -n "${spring_ai_base}" ]] && pass "Spring AI Base URL：已配置" || fail "缺少 URBAN_SAFE_SPRING_AI_BASE_URL"

echo
info "6/7 PDF 风险报告运行环境"
if command -v fc-list >/dev/null 2>&1; then
  if fc-list :lang=zh family 2>/dev/null | grep -q '[^[:space:]]'; then
    pass "中文字体：已检测到可用于 PDF 的中文字体"
  else
    fail "未检测到中文字体；风险报告会拒绝生成。Ubuntu/WSL：sudo apt update && sudo apt install -y fonts-noto-cjk"
  fi
else
  warn "未安装 fontconfig，无法自动检测中文字体；建议安装 fontconfig + fonts-noto-cjk"
fi

if [[ "$(value_of URBAN_SAFE_STORAGE_PROVIDER)" == "MINIO" ]]; then
  pass "报告存储：MINIO"
else
  warn "当前报告存储不是 MINIO：$(value_of URBAN_SAFE_STORAGE_PROVIDER)"
fi

if [[ "${connectivity}" == "true" ]]; then
  echo
  info "7/7 只读连通性探测"

  db_host="$(value_of URBAN_SAFE_DB_HOST)"
  db_port="$(value_of URBAN_SAFE_DB_PORT)"
  if [[ -n "${db_host}" && -n "${db_port}" ]] && check_tcp "${db_host}" "${db_port}"; then
    pass "PostgreSQL TCP：${db_host}:${db_port} 可连接"
  else
    fail "PostgreSQL TCP：无法连接 ${db_host:-?}:${db_port:-?}"
  fi

  minio_host="$(value_of URBAN_SAFE_MINIO_HOST)"
  minio_port="$(value_of URBAN_SAFE_MINIO_API_PORT)"
  minio_code="$(http_status "http://${minio_host}:${minio_port}/minio/health/live")"
  if [[ "${minio_code}" =~ ^2 ]]; then
    pass "MinIO Health：HTTP ${minio_code}"
  else
    fail "MinIO Health：HTTP ${minio_code}"
  fi

  ai_base="$(value_of URBAN_SAFE_AI_SERVICE_BASE_URL)"
  ai_code="$(http_status "${ai_base%/}/internal/api/v1/ai/ready")"
  if [[ "${ai_code}" =~ ^2 ]]; then
    pass "FastAPI READY：HTTP ${ai_code}"
  else
    fail "FastAPI READY：HTTP ${ai_code}"
  fi

  server_port="$(value_of URBAN_SAFE_SERVER_PORT)"
  boot_code="$(http_status "http://127.0.0.1:${server_port}/actuator/health")"
  if [[ "${boot_code}" =~ ^2 ]]; then
    pass "Spring Boot Health：HTTP ${boot_code}"
  else
    warn "Spring Boot Health：HTTP ${boot_code}；若后端尚未启动可忽略"
  fi

  if is_true "${dify_enabled}" && [[ -n "${review_assist_key}" && -n "${dify_base}" ]]; then
    dify_code="$(http_status "${dify_base%/}/workflows/logs?page=1&limit=1" -H "Authorization: Bearer ${review_assist_key}")"
    if [[ "${dify_code}" =~ ^2 ]]; then
      pass "Dify Review Assist 连通性：HTTP ${dify_code}（专用密钥未输出）"
    elif [[ "${dify_code}" == "401" || "${dify_code}" == "403" ]]; then
      fail "Dify Review Assist 连通性：HTTP ${dify_code}，请检查专用工作流 API Key"
    else
      warn "Dify Review Assist 连通性：HTTP ${dify_code}；请结合 Dify 服务状态与 Base URL 排查"
    fi
  else
    warn "Dify Review Assist 连通性：未执行（未启用或缺少专用工作流 Key）"
  fi

  if is_true "${spring_ai_enabled}" && [[ "${spring_ai_chat,,}" == "openai" && -n "${spring_ai_key}" && -n "${spring_ai_base}" ]]; then
    deepseek_code="$(http_status "${spring_ai_base%/}/models" -H "Authorization: Bearer ${spring_ai_key}")"
    if [[ "${deepseek_code}" =~ ^2 ]]; then
      pass "Spring AI / DeepSeek 连通性：HTTP ${deepseek_code}"
    elif [[ "${deepseek_code}" == "401" || "${deepseek_code}" == "403" ]]; then
      fail "Spring AI / DeepSeek 连通性：HTTP ${deepseek_code}，请检查 API Key"
    else
      warn "Spring AI / DeepSeek 连通性：HTTP ${deepseek_code}；请检查 Base URL 或供应商状态"
    fi
  else
    warn "Spring AI / DeepSeek 连通性：未执行（Provider、Chat 模型或 Key 未就绪）"
  fi
else
  echo
  info "7/7 连通性探测已跳过；需要时追加 --connectivity"
fi

echo
echo "========================================"
echo " 检测结果：PASS=${pass_count} WARN=${warn_count} FAIL=${fail_count}"
echo "========================================"

if (( fail_count > 0 )); then
  echo "建议先修复上方 FAIL 项，再重启 Spring Boot/FastAPI 后重新检测。" >&2
  exit 1
fi

if (( warn_count > 0 )); then
  echo "配置可继续运行，但存在 WARN 项；比赛演示前建议逐项确认。"
else
  echo "当前检测项全部通过。"
fi
