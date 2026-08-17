#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${URBAN_SAFE_ENV_FILE:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
base_generator="${repository_root}/scripts/dev/generate-showcase-data.sh"
preflight_script="${repository_root}/scripts/dev/validate-wuhan-competition-generator.sh"
asset_script="${repository_root}/scripts/dev/prepare-showcase-assets.sh"
closure_generator="${repository_root}/scripts/dev/generate-showcase-closure.py"
sql_normalizer="${repository_root}/scripts/dev/normalize-showcase-diversity-sql.py"
operations_sql="${repository_root}/scripts/dev/enrich-wuhan-competition-operations.sql"
decision_sql="${repository_root}/scripts/dev/enrich-feedback-reinspection-decisions.sql"
assessment_script="${repository_root}/scripts/dev/calculate-showcase-assessments.sh"
risk_coverage_sql="${repository_root}/scripts/dev/ensure-wuhan-competition-risk-coverage.sql"
verify_sql="${repository_root}/scripts/dev/verify-wuhan-competition-data.sql"
decision_verify_sql="${repository_root}/scripts/dev/verify-feedback-reinspection-decisions.sql"
public_catalog="${SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE:-${repository_root}/data/showcase-sources/wuhan-old-community-catalog-v1.json}"
asset_dir="${SHOWCASE_ASSET_DIR:-${repository_root}/data/showcase-assets/inspection}"

usage() {
  cat <<'EOF'
生成武汉 100 小区比赛级完整展示数据。

推荐：
  bash scripts/dev/generate-wuhan-competition-data.sh --mode clean

调试/复跑：
  bash scripts/dev/generate-wuhan-competition-data.sh --mode incremental
  bash scripts/dev/generate-wuhan-competition-data.sh --cache-mode only --mode clean

默认规模与保护策略：
  SHOWCASE_COMMUNITY_COUNT=100
  SHOWCASE_BUILDINGS_PER_COMMUNITY=8
  SHOWCASE_HISTORY_MONTHS=24
  SHOWCASE_DATA_DENSITY=rich
  SHOWCASE_AMAP_CACHE_MODE=prefer
  SHOWCASE_AMAP_MAX_NETWORK_REQUESTS=520
  SHOWCASE_AMAP_HTTP_ATTEMPTS=2
  SHOWCASE_AMAP_CACHE_FLUSH_EVERY=1
  SHOWCASE_WUHAN_RESERVE_PER_DISTRICT=12

脚本先运行完全离线的语法/单元测试；通过后才允许发起高德请求。
最终只有 100 小区逐楼栋闭环和复检人工决策 real-mode 专项硬闸门全部通过才打印 [PASS]。
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

required_files=(
  "${env_file}" "${compose_file}" "${base_generator}" "${preflight_script}"
  "${asset_script}" "${closure_generator}" "${sql_normalizer}" "${operations_sql}" "${decision_sql}"
  "${assessment_script}" "${risk_coverage_sql}" "${verify_sql}" "${decision_verify_sql}" "${public_catalog}"
)
for file in "${required_files[@]}"; do
  [[ -f "${file}" ]] || { echo "缺少必要文件：${file}" >&2; exit 1; }
done
for command in docker python3 curl; do
  command -v "${command}" >/dev/null 2>&1 || { echo "缺少命令：${command}" >&2; exit 1; }
done

export SHOWCASE_COMMUNITY_COUNT="${SHOWCASE_COMMUNITY_COUNT:-100}"
export SHOWCASE_BUILDINGS_PER_COMMUNITY="${SHOWCASE_BUILDINGS_PER_COMMUNITY:-8}"
export SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY="${SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY:-10}"
export SHOWCASE_MAX_POI_PAGES="${SHOWCASE_MAX_POI_PAGES:-3}"
export SHOWCASE_HISTORY_MONTHS="${SHOWCASE_HISTORY_MONTHS:-24}"
export SHOWCASE_DATA_DENSITY="${SHOWCASE_DATA_DENSITY:-rich}"
export SHOWCASE_DIVERSITY_PROFILE="${SHOWCASE_DIVERSITY_PROFILE:-balanced}"
export SHOWCASE_AMAP_CACHE_MODE="${SHOWCASE_AMAP_CACHE_MODE:-prefer}"
export SHOWCASE_AMAP_MAX_NETWORK_REQUESTS="${SHOWCASE_AMAP_MAX_NETWORK_REQUESTS:-520}"
export SHOWCASE_AMAP_HTTP_ATTEMPTS="${SHOWCASE_AMAP_HTTP_ATTEMPTS:-2}"
export SHOWCASE_AMAP_CACHE_FLUSH_EVERY="${SHOWCASE_AMAP_CACHE_FLUSH_EVERY:-1}"
export SHOWCASE_WUHAN_RESERVE_PER_DISTRICT="${SHOWCASE_WUHAN_RESERVE_PER_DISTRICT:-12}"
export SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE="${public_catalog}"
# 风险评分必须在 AI/复核/治理证据全部补齐后统一执行。
export SHOWCASE_CALCULATE_ASSESSMENTS=0

(( SHOWCASE_COMMUNITY_COUNT >= 100 )) || { echo "比赛档小区数必须 >= 100。" >&2; exit 2; }
(( SHOWCASE_BUILDINGS_PER_COMMUNITY >= 8 )) || { echo "比赛档每小区楼栋数必须 >= 8。" >&2; exit 2; }
(( SHOWCASE_AMAP_MAX_NETWORK_REQUESTS >= 0 )) || { echo "高德网络请求预算不能为负数。" >&2; exit 2; }

# 任何语法/单元测试错误都必须在消耗高德 API 额度之前暴露。
echo "[0/8] 执行离线生成器预检（不会访问高德）..."
bash "${preflight_script}"

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
for service in postgresql minio; do
  "${compose[@]}" ps --status running --services | grep -qx "${service}" || {
    echo "${service} 服务未运行。" >&2
    exit 1
  }
done

available_kb="$(awk '/MemAvailable:/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)"
if [[ "${available_kb}" =~ ^[0-9]+$ ]] && (( available_kb > 0 )); then
  available_gb=$((available_kb / 1024 / 1024))
  echo "[resource] 当前 Linux/WSL 可用内存约 ${available_gb} GB。"
  (( available_gb >= 8 )) || echo "[resource] 警告：可用内存低于 8GB；建议关闭其他高内存任务。" >&2
fi

read_dotenv_value() {
  ENV_FILE="${env_file}" ENV_KEY="$1" ENV_FALLBACK="${2:-}" python3 <<'PY'
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
PY
}

base_args=("$@")
[[ ${#base_args[@]} -gt 0 ]] || base_args=(--mode clean)

echo "============================================================"
echo " 武汉 100 小区比赛数据生成"
echo " 目标：${SHOWCASE_COMMUNITY_COUNT} 小区 / 每小区至少 ${SHOWCASE_BUILDINGS_PER_COMMUNITY} 栋"
echo " 高德 HTTP 尝试预算：${SHOWCASE_AMAP_MAX_NETWORK_REQUESTS}（缓存命中不计费）"
echo "============================================================"

echo "[1/8] 生成真实武汉空间底座与 24 个月历史业务数据..."
bash "${base_generator}" "${base_args[@]}"

echo "[2/8] 生成并上传无人物/车牌的演示巡检图片..."
SHOWCASE_ASSET_DIR="${asset_dir}" bash "${asset_script}" "${env_file}"
asset_bucket="${SHOWCASE_ASSET_BUCKET:-$(read_dotenv_value URBAN_SAFE_MINIO_ASSETS_BUCKET '')}"
[[ -n "${asset_bucket}" ]] || { echo "无法解析 URBAN_SAFE_MINIO_ASSETS_BUCKET。" >&2; exit 3; }

closure_sql="$(mktemp)"
trap 'rm -f "${closure_sql}"' EXIT

echo "[3/8] 为每栋楼补齐巡检 → AI → 人工复核 → 治理闭环..."
SHOWCASE_ASSET_MANIFEST="${asset_dir}/manifest.json" \
SHOWCASE_ASSET_BUCKET="${asset_bucket}" \
SHOWCASE_CLOSURE_SQL_FILE="${closure_sql}" \
SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE="${public_catalog}" \
SHOWCASE_HISTORY_MONTHS="${SHOWCASE_HISTORY_MONTHS}" \
python3 "${closure_generator}"
# row_number() 在 PostgreSQL 中返回 bigint，而 make_interval 的 days/hours 参数要求 integer。
# 在执行闭环 SQL 前统一规范化，避免后续新增时间分布表达式再次触发类型不匹配。
SHOWCASE_SQL_FILE="${closure_sql}" python3 "${sql_normalizer}"
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${closure_sql}"

echo "[4/8] 补齐 AI 结构化结果、降级审计与居民反馈时间线..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${operations_sql}"

echo "[5/8] 补齐复检系统建议 + 人工最终决策 real-mode 专项样例..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${decision_sql}"

echo "[6/8] 使用完整闭环输入为全部楼栋计算正式风险评分..."
SHOWCASE_ASSESSMENT_LIMIT=0 SHOWCASE_MIN_ASSESSMENT_SUCCESS_RATE=100 \
  bash "${assessment_script}" "${env_file}"

echo "[7/8] 整理比赛风险等级/治理优先级分布（不制造 NO_RESULT）..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${risk_coverage_sql}"

echo "[8/8] 执行 100 小区逐楼栋 + 复检人工决策完整性硬闸门..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${verify_sql}"
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -P pager=off -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${decision_verify_sql}"

echo "============================================================"
echo " [PASS] 武汉 100 小区比赛展示数据已通过完整性闸门"
echo "============================================================"
echo "建议首轮成功后再用 --cache-mode only --mode clean 验证缓存可独立重建。"
