#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${URBAN_SAFE_ENV_FILE:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
base_seed_script="${repository_root}/scripts/dev/seed-demo-data.sh"
generator_py="${repository_root}/scripts/dev/generate-showcase-data-cached.py"
diversity_py="${repository_root}/scripts/dev/enhance-showcase-diversity.py"
normalize_diversity_py="${repository_root}/scripts/dev/normalize-showcase-diversity-sql.py"
assessment_script="${repository_root}/scripts/dev/calculate-showcase-assessments.sh"
coverage_sql_file="${repository_root}/scripts/dev/ensure-showcase-status-coverage.sql"

city="${SHOWCASE_CITY:-武汉}"
seed="${SHOWCASE_SEED:-20260810}"
run_base_seed="${SHOWCASE_RUN_BASE_SEED:-1}"
update_map_center="${SHOWCASE_UPDATE_MAP_CENTER:-1}"
calculate_assessments="${SHOWCASE_CALCULATE_ASSESSMENTS:-1}"
diversity_profile="${SHOWCASE_DIVERSITY_PROFILE:-balanced}"
history_months="${SHOWCASE_HISTORY_MONTHS:-24}"
data_density="${SHOWCASE_DATA_DENSITY:-rich}"
generation_mode="${SHOWCASE_GENERATION_MODE:-incremental}"
amap_cache_mode="${SHOWCASE_AMAP_CACHE_MODE:-prefer}"
amap_cache_file="${SHOWCASE_AMAP_CACHE_FILE:-${repository_root}/data/showcase-cache/amap-query-cache-v1.json}"

usage() {
  cat <<'EOF'
生成城市中心城区的大规模系统展示数据。

生成模式：
  incremental  默认。保留现有小区、楼栋、地图与业务数据，按稳定业务编码执行新增/更新。
  clean        先清空小区、楼栋、空间地图以及依赖这些对象的巡检、反馈、评分、报告、AI 推理等业务数据，再重新生成。

高德缓存模式：
  prefer   默认。优先读取本地缓存；单个请求未命中时才访问高德并立即回写缓存。支持部分缓存和全部缓存。
  only     只读缓存，完全离线；任一请求缺失立即报错。
  refresh  忽略已有缓存，全部重新访问高德并覆盖缓存。
  off      禁用缓存，每次都访问高德。

用法：
  bash scripts/dev/generate-showcase-data.sh
  bash scripts/dev/generate-showcase-data.sh --mode incremental
  bash scripts/dev/generate-showcase-data.sh --mode clean
  bash scripts/dev/generate-showcase-data.sh --incremental
  bash scripts/dev/generate-showcase-data.sh --clean
  bash scripts/dev/generate-showcase-data.sh --cache-mode prefer
  bash scripts/dev/generate-showcase-data.sh --cache-mode only
  bash scripts/dev/generate-showcase-data.sh --cache-mode refresh
  bash scripts/dev/generate-showcase-data.sh --cache-file /path/to/amap-cache.json

也可通过环境变量指定：
  SHOWCASE_GENERATION_MODE=incremental bash scripts/dev/generate-showcase-data.sh
  SHOWCASE_GENERATION_MODE=clean bash scripts/dev/generate-showcase-data.sh
  SHOWCASE_AMAP_CACHE_MODE=prefer bash scripts/dev/generate-showcase-data.sh
  SHOWCASE_AMAP_CACHE_FILE=/path/to/amap-cache.json bash scripts/dev/generate-showcase-data.sh

默认策略：
  - 城市：武汉；
  - 从高德地点搜索 2.0 获取真实住宅 POI；
  - 逆地理编码获取 AOI 标识、面积、街道等真实元数据；
  - 尽量从高德周边 POI 获取真实楼栋名称和真实楼栋点位；
  - 高德查询默认按请求粒度缓存到本地文件，缓存文件不保存高德 Key；
  - 高德公开 API 不返回住宅小区/单栋楼的 Polygon，因此脚本不伪造矩形边界；
  - 数据大屏使用高德官方行政区边界和 Buildings 楼块图层表达真实区域/建筑轮廓；
  - 基础档案写入后，再执行社区画像驱动的场景增强；
  - 默认生成 24 个月历史，覆盖巡检、现场记录、证据、居民反馈和多种处理状态；
  - 默认批量调用系统现有风险评分接口，为新楼栋生成风险分布；
  - 评分结束后仅对 SHOWCASE-WH-* 数据执行展示状态覆盖，保证 LOW/MEDIUM/HIGH/VERY_HIGH、P1/P2/P3/P4、CURRENT/STALE/NO_RESULT 均有样本。

常用参数：
  SHOWCASE_GENERATION_MODE=incremental|clean
  SHOWCASE_AMAP_CACHE_MODE=prefer|only|refresh|off
  SHOWCASE_AMAP_CACHE_FILE=data/showcase-cache/amap-query-cache-v1.json
  SHOWCASE_CITY=武汉
  SHOWCASE_SEED=20260810
  SHOWCASE_COMMUNITY_COUNT=36
  SHOWCASE_BUILDINGS_PER_COMMUNITY=8
  SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY=10
  SHOWCASE_DISTRICTS=江岸区,江汉区,硚口区,汉阳区,武昌区,青山区,洪山区
  SHOWCASE_DIVERSITY_PROFILE=balanced
  SHOWCASE_HISTORY_MONTHS=24
  SHOWCASE_DATA_DENSITY=rich
  SHOWCASE_RUN_BASE_SEED=1
  SHOWCASE_CALCULATE_ASSESSMENTS=1
  SHOWCASE_ASSESSMENT_LIMIT=0
  SHOWCASE_UPDATE_MAP_CENTER=1

数据密度：
  light     快速开发预览，历史业务记录较少；
  balanced  常规演示，记录数量适中；
  rich      数据大屏/现场展示，默认，生成更丰富的历史链。

clean 模式边界：
  - 清空 core.community，并通过数据库外键级联清理楼栋及其巡检、反馈、评分、报告、AI 推理等依赖业务表；
  - 清空 geo.spatial_boundary_revision 与 geo.hazard_zone 等独立空间历史/地图数据；
  - 清理与被删除业务对象直接绑定的文件元数据、通用绑定、向量、Outbox 与审计资源引用；
  - 保留用户、角色、评分规则、AI 模型登记、系统配置和 Flyway 迁移历史；
  - 仅清理数据库元数据，不删除 MinIO/对象存储中的物理文件；
  - clean 不删除 data/showcase-cache 下的高德本地缓存，因此可反复重建数据库而不重复消耗查询额度。

说明：
  - 高德 Key 仅从本地 .env 读取，不写入 Git、SQL、缓存或日志；
  - cache-only 模式允许在本地缓存完整时不配置可用高德 Key；
  - .env 只按 dotenv 文本解析，绝不作为 Bash 脚本执行；
  - 若高德无法返回单栋楼 POI，会创建业务楼栋档案但不伪造空间坐标和 Polygon；
  - 只有具有真实高德楼栋 POI 的楼栋才写入 geo.building_location；
  - 现有人工确认/导入的真实边界不会被覆盖；已知 syntheticBoundary 会退出正式展示；
  - 社区画像、人口、楼龄、巡检、证据、上报等属于固定种子的场景化业务数据；
  - 状态覆盖只作用于 SHOWCASE-WH-* 展示楼栋，并在评分 input_snapshot 中标记 showcaseCoverage=true。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --mode)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "--mode 需要指定 clean 或 incremental。" >&2
        exit 2
      fi
      generation_mode="$2"
      shift 2
      ;;
    --clean)
      generation_mode="clean"
      shift
      ;;
    --incremental)
      generation_mode="incremental"
      shift
      ;;
    --cache-mode)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "--cache-mode 需要指定 prefer、only、refresh 或 off。" >&2
        exit 2
      fi
      amap_cache_mode="$2"
      shift 2
      ;;
    --cache-file)
      if [[ $# -lt 2 || -z "${2:-}" ]]; then
        echo "--cache-file 需要指定缓存文件路径。" >&2
        exit 2
      fi
      amap_cache_file="$2"
      shift 2
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

case "${generation_mode}" in
  clean|incremental) ;;
  *)
    echo "SHOWCASE_GENERATION_MODE/--mode 仅支持 clean 或 incremental，当前值：${generation_mode}" >&2
    exit 2
    ;;
esac

case "${amap_cache_mode}" in
  prefer|only|refresh|off) ;;
  *)
    echo "SHOWCASE_AMAP_CACHE_MODE/--cache-mode 仅支持 prefer、only、refresh 或 off，当前值：${amap_cache_mode}" >&2
    exit 2
    ;;
esac

for required_file in "${env_file}" "${compose_file}" "${base_seed_script}" "${generator_py}" "${diversity_py}" "${normalize_diversity_py}" "${assessment_script}" "${coverage_sql_file}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "缺少必要文件：${required_file}" >&2
    exit 1
  fi
done
for command in docker python3; do
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

amap_key="${URBAN_SAFE_AMAP_WEB_SERVICE_KEY:-$(read_dotenv_value URBAN_SAFE_AMAP_WEB_SERVICE_KEY '')}"
amap_base="${URBAN_SAFE_AMAP_WEB_SERVICE_BASE_URL:-$(read_dotenv_value URBAN_SAFE_AMAP_WEB_SERVICE_BASE_URL 'https://restapi.amap.com')}"
if [[ -z "${amap_key}" ]]; then
  if [[ "${amap_cache_mode}" == "only" ]]; then
    amap_key="CACHE_ONLY_NO_NETWORK"
    echo "[cache] only 模式且未配置高德 Key：将完全依赖本地缓存。"
  else
    echo "URBAN_SAFE_AMAP_WEB_SERVICE_KEY 为空，请先在本地 .env 配置高德 Web Service Key；或使用 --cache-mode only 读取完整缓存。" >&2
    exit 1
  fi
fi

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
if ! "${compose[@]}" ps --status running --services | grep -qx 'postgresql'; then
  echo "PostgreSQL 服务未运行。" >&2
  exit 1
fi

clean_showcase_domain() {
  echo
  echo "[clean] 清空地图、小区、楼栋及其依赖业务数据..."
  echo "[clean] 保留：账号/角色、评分规则、AI 模型登记、系统配置、Flyway 历史、本地高德查询缓存。"
  "${compose[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_CLEAN'
BEGIN;

CREATE TEMP TABLE showcase_clean_business_ids (
  id UUID PRIMARY KEY
) ON COMMIT DROP;

INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.community
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.building
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.inspection_task
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.inspection_record
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.building_evidence
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM core.resident_report
ON CONFLICT DO NOTHING;
INSERT INTO showcase_clean_business_ids(id)
SELECT id FROM ai.inference_task
ON CONFLICT DO NOTHING;

TRUNCATE TABLE geo.spatial_boundary_revision, geo.hazard_zone;
TRUNCATE TABLE core.community CASCADE;

DELETE FROM ai.embedding e
USING showcase_clean_business_ids ids
WHERE e.entity_id = ids.id;

DELETE FROM asset.asset_binding binding
USING showcase_clean_business_ids ids
WHERE binding.business_id = ids.id;

DELETE FROM asset.file_asset file
USING showcase_clean_business_ids ids
WHERE file.business_id = ids.id
  AND NOT EXISTS (
    SELECT 1 FROM ai.model_registry model
    WHERE model.artifact_asset_id = file.id
  );

DELETE FROM integration.outbox_event event
USING showcase_clean_business_ids ids
WHERE event.aggregate_id = ids.id;

DELETE FROM audit.operation_log log
USING showcase_clean_business_ids ids
WHERE log.resource_id = ids.id;

COMMIT;
SQL_CLEAN

  local clean_stats
  clean_stats="$(
    "${compose[@]}" exec -T postgresql sh -eu -c \
      'psql -v ON_ERROR_STOP=1 -At -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_CLEAN_STATS'
SELECT
  (SELECT COUNT(*) FROM core.community),
  (SELECT COUNT(*) FROM core.building),
  (SELECT COUNT(*) FROM geo.community_location),
  (SELECT COUNT(*) FROM geo.building_location),
  (SELECT COUNT(*) FROM geo.community_boundary),
  (SELECT COUNT(*) FROM geo.building_boundary),
  (SELECT COUNT(*) FROM geo.spatial_boundary_revision),
  (SELECT COUNT(*) FROM geo.hazard_zone);
SQL_CLEAN_STATS
  )"
  local remaining_communities remaining_buildings remaining_community_locations remaining_building_locations
  local remaining_community_boundaries remaining_building_boundaries remaining_revisions remaining_hazards
  IFS='|' read -r remaining_communities remaining_buildings remaining_community_locations remaining_building_locations \
    remaining_community_boundaries remaining_building_boundaries remaining_revisions remaining_hazards <<<"${clean_stats}"

  echo "[clean] 清理后小区：${remaining_communities:-0}，楼栋：${remaining_buildings:-0}，小区点位：${remaining_community_locations:-0}，楼栋点位：${remaining_building_locations:-0}"
  echo "[clean] 清理后小区边界：${remaining_community_boundaries:-0}，楼栋边界：${remaining_building_boundaries:-0}，边界修订：${remaining_revisions:-0}，风险区：${remaining_hazards:-0}"

  if (( ${remaining_communities:-0} != 0 || ${remaining_buildings:-0} != 0 \
        || ${remaining_community_locations:-0} != 0 || ${remaining_building_locations:-0} != 0 \
        || ${remaining_community_boundaries:-0} != 0 || ${remaining_building_boundaries:-0} != 0 \
        || ${remaining_revisions:-0} != 0 || ${remaining_hazards:-0} != 0 )); then
    echo "clean 模式校验失败：地图/小区/楼栋相关核心数据未完全清空。" >&2
    exit 5
  fi
}

if [[ "${generation_mode}" == "clean" ]]; then
  clean_showcase_domain
else
  echo "[incremental] 保留现有地图、小区、楼栋与业务数据，执行幂等新增/更新。"
fi

echo "[cache] mode=${amap_cache_mode} file=${amap_cache_file}"

if [[ "${run_base_seed}" == "1" ]]; then
  echo "[1/7] 初始化基础业务链数据..."
  bash "${base_seed_script}" "${env_file}"
else
  echo "[1/7] 跳过基础业务链初始化。"
fi

sql_file="$(mktemp)"
diversity_sql_file="$(mktemp)"
meta_file="$(mktemp)"
trap 'rm -f "${sql_file}" "${diversity_sql_file}" "${meta_file}"' EXIT

echo "[2/7] 获取武汉中心城区住宅/楼栋真实点位并生成基础业务数据..."
AMAP_KEY="${amap_key}" \
AMAP_BASE="${amap_base}" \
SHOWCASE_AMAP_CACHE_MODE="${amap_cache_mode}" \
SHOWCASE_AMAP_CACHE_FILE="${amap_cache_file}" \
SHOWCASE_CITY="${city}" \
SHOWCASE_SEED="${seed}" \
SHOWCASE_SQL_FILE="${sql_file}" \
SHOWCASE_META_FILE="${meta_file}" \
SHOWCASE_COMMUNITY_COUNT="${SHOWCASE_COMMUNITY_COUNT:-36}" \
SHOWCASE_BUILDINGS_PER_COMMUNITY="${SHOWCASE_BUILDINGS_PER_COMMUNITY:-8}" \
SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY="${SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY:-10}" \
SHOWCASE_MAX_POI_PAGES="${SHOWCASE_MAX_POI_PAGES:-3}" \
SHOWCASE_DISTRICTS="${SHOWCASE_DISTRICTS:-江岸区,江汉区,硚口区,汉阳区,武昌区,青山区,洪山区}" \
python3 "${generator_py}"

echo "[3/7] 写入 PostgreSQL/PostGIS 城市基础样例数据..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${sql_file}"

echo "[4/7] 生成并写入社区画像与多样化历史业务数据..."
SHOWCASE_SEED="${seed}" \
SHOWCASE_DIVERSITY_PROFILE="${diversity_profile}" \
SHOWCASE_HISTORY_MONTHS="${history_months}" \
SHOWCASE_DATA_DENSITY="${data_density}" \
SHOWCASE_DIVERSITY_SQL_FILE="${diversity_sql_file}" \
python3 "${diversity_py}"

SHOWCASE_DIVERSITY_SQL_FILE="${diversity_sql_file}" \
python3 "${normalize_diversity_py}"

"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${diversity_sql_file}"

if [[ "${update_map_center}" == "1" ]]; then
  echo "[5/7] 更新本地地图默认中心点..."
  SHOWCASE_META_FILE="${meta_file}" ENV_FILE="${env_file}" python3 <<'PY_CENTER'
import json
import os
from pathlib import Path
meta = json.loads(Path(os.environ['SHOWCASE_META_FILE']).read_text(encoding='utf-8'))
env_path = Path(os.environ['ENV_FILE'])
updates = {
    'URBAN_SAFE_MAP_DEFAULT_CENTER_LONGITUDE': str(meta['centerLongitude']),
    'URBAN_SAFE_MAP_DEFAULT_CENTER_LATITUDE': str(meta['centerLatitude']),
    'URBAN_SAFE_MAP_DEFAULT_ZOOM': '13',
}
lines = env_path.read_text(encoding='utf-8').splitlines()
seen = set()
out = []
for line in lines:
    key = line.split('=', 1)[0].strip() if '=' in line else ''
    if key in updates:
        out.append(f"{key}={updates[key]}")
        seen.add(key)
    else:
        out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")
env_path.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY_CENTER
else
  echo "[5/7] 跳过地图默认中心点更新。"
fi

if [[ "${calculate_assessments}" == "1" ]]; then
  echo "[6/7] 批量生成城市样例楼栋风险评分..."
  SHOWCASE_ASSESSMENT_LIMIT="${SHOWCASE_ASSESSMENT_LIMIT:-0}" \
    bash "${assessment_script}" "${env_file}"

  echo "[7/7] 保证风险、优先级与结果新鲜度状态全覆盖..."
  "${compose[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "${coverage_sql_file}"
else
  echo "[6/7] 跳过风险评分计算。"
  echo "[7/7] 因风险评分已关闭，跳过风险/优先级状态覆盖。"
fi

echo
echo "[校验] 检查数据库中的地图展示数据..."
db_stats="$(
  "${compose[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -At -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_STATS'
SELECT
  (SELECT COUNT(*) FROM core.community c WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'),
  (SELECT COUNT(*) FROM geo.community_location cl JOIN core.community c ON c.id=cl.community_id WHERE cl.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'),
  (SELECT COUNT(*) FROM core.building b JOIN core.community c ON c.id=b.community_id WHERE b.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'),
  (SELECT COUNT(*) FROM geo.building_location bl JOIN core.building b ON b.id=bl.building_id JOIN core.community c ON c.id=b.community_id WHERE bl.deleted_at IS NULL AND b.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'),
  ((SELECT COUNT(*) FROM geo.community_boundary cb JOIN core.community c ON c.id=cb.community_id WHERE cb.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%' AND cb.status='VERIFIED') +
   (SELECT COUNT(*) FROM geo.building_boundary bb JOIN core.building b ON b.id=bb.building_id JOIN core.community c ON c.id=b.community_id WHERE bb.deleted_at IS NULL AND b.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%' AND bb.status='VERIFIED')),
  ((SELECT COUNT(*) FROM geo.community_boundary cb JOIN core.community c ON c.id=cb.community_id WHERE cb.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%' AND COALESCE(cb.metadata->>'showcaseSuppressed','false')='true') +
   (SELECT COUNT(*) FROM geo.building_boundary bb JOIN core.building b ON b.id=bb.building_id JOIN core.community c ON c.id=b.community_id WHERE bb.deleted_at IS NULL AND b.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%' AND COALESCE(bb.metadata->>'showcaseSuppressed','false')='true'));
SQL_STATS
)"
IFS='|' read -r showcase_communities located_communities showcase_buildings located_buildings verified_boundaries suppressed_boundaries <<<"${db_stats}"

echo "  生成模式：${generation_mode}"
echo "  高德缓存模式：${amap_cache_mode}"
echo "  高德缓存文件：${amap_cache_file}"
echo "  样例小区：${showcase_communities:-0}"
echo "  已定位小区：${located_communities:-0}"
echo "  业务楼栋：${showcase_buildings:-0}"
echo "  已定位楼栋：${located_buildings:-0}"
echo "  已确认真实边界：${verified_boundaries:-0}"
echo "  已抑制合成边界：${suppressed_boundaries:-0}"

if (( ${showcase_communities:-0} > 0 && ${located_communities:-0} == 0 )); then
  echo "生成后校验失败：已经生成样例小区，但 geo.community_location 中没有可用小区位置。" >&2
  exit 4
fi

if [[ "${calculate_assessments}" == "1" ]]; then
  echo
  echo "[校验] 检查展示状态覆盖..."
  coverage_stats="$(
    "${compose[@]}" exec -T postgresql sh -eu -c \
      'psql -v ON_ERROR_STOP=1 -At -F "|" -U "$POSTGRES_USER" -d "$POSTGRES_DB"' <<'SQL_COVERAGE_STATS'
WITH showcase_buildings AS (
  SELECT b.id
  FROM core.building b
  JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
  WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
), dashboard_like AS (
  SELECT b.id,
         r.risk_level,
         p.priority_level,
         CASE WHEN r.id IS NULL THEN 'NO_RESULT'
              WHEN r.status='STALE' OR co.status='STALE' OR p.status='STALE' THEN 'STALE'
              ELSE 'CURRENT' END freshness
  FROM showcase_buildings b
  LEFT JOIN LATERAL (
    SELECT x.* FROM core.risk_assessment x
    WHERE x.building_id=b.id AND x.status IN ('CURRENT','STALE')
    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END, x.assessed_at DESC, x.id DESC
    LIMIT 1
  ) r ON TRUE
  LEFT JOIN LATERAL (
    SELECT x.* FROM core.completeness_assessment x
    WHERE x.building_id=b.id AND x.status IN ('CURRENT','STALE')
    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END, x.assessed_at DESC, x.id DESC
    LIMIT 1
  ) co ON TRUE
  LEFT JOIN LATERAL (
    SELECT x.* FROM core.renewal_priority x
    WHERE x.building_id=b.id AND x.ranking_scope_key='ALL' AND x.status IN ('CURRENT','STALE')
    ORDER BY CASE x.status WHEN 'CURRENT' THEN 0 ELSE 1 END, x.generated_at DESC, x.id DESC
    LIMIT 1
  ) p ON TRUE
)
SELECT
  COUNT(*) FILTER (WHERE risk_level='LOW'),
  COUNT(*) FILTER (WHERE risk_level='MEDIUM'),
  COUNT(*) FILTER (WHERE risk_level='HIGH'),
  COUNT(*) FILTER (WHERE risk_level='VERY_HIGH'),
  COUNT(*) FILTER (WHERE priority_level='P1'),
  COUNT(*) FILTER (WHERE priority_level='P2'),
  COUNT(*) FILTER (WHERE priority_level='P3'),
  COUNT(*) FILTER (WHERE priority_level='P4'),
  COUNT(*) FILTER (WHERE freshness='CURRENT'),
  COUNT(*) FILTER (WHERE freshness='STALE'),
  COUNT(*) FILTER (WHERE freshness='NO_RESULT'),
  (SELECT COUNT(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND t.deleted_at IS NULL AND t.status='PENDING'),
  (SELECT COUNT(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND t.deleted_at IS NULL AND t.status='IN_PROGRESS'),
  (SELECT COUNT(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND t.deleted_at IS NULL AND t.status='COMPLETED'),
  (SELECT COUNT(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND t.deleted_at IS NULL AND t.status='CANCELLED')
FROM dashboard_like;
SQL_COVERAGE_STATS
  )"
  IFS='|' read -r risk_low risk_medium risk_high risk_very_high priority_p1 priority_p2 priority_p3 priority_p4 freshness_current freshness_stale freshness_no_result task_pending task_in_progress task_completed task_cancelled <<<"${coverage_stats}"

  echo "  风险 LOW/MEDIUM/HIGH/VERY_HIGH：${risk_low:-0}/${risk_medium:-0}/${risk_high:-0}/${risk_very_high:-0}"
  echo "  优先级 P1/P2/P3/P4：${priority_p1:-0}/${priority_p2:-0}/${priority_p3:-0}/${priority_p4:-0}"
  echo "  结果 CURRENT/STALE/NO_RESULT：${freshness_current:-0}/${freshness_stale:-0}/${freshness_no_result:-0}"
  echo "  巡检 PENDING/IN_PROGRESS/COMPLETED/CANCELLED：${task_pending:-0}/${task_in_progress:-0}/${task_completed:-0}/${task_cancelled:-0}"

  for value in "${risk_low:-0}" "${risk_medium:-0}" "${risk_high:-0}" "${risk_very_high:-0}" \
               "${priority_p1:-0}" "${priority_p2:-0}" "${priority_p3:-0}" "${priority_p4:-0}" \
               "${freshness_current:-0}" "${freshness_stale:-0}" "${freshness_no_result:-0}" \
               "${task_pending:-0}" "${task_in_progress:-0}" "${task_completed:-0}" "${task_cancelled:-0}"; do
    if (( value <= 0 )); then
      echo "生成后校验失败：核心展示状态未实现全覆盖。" >&2
      exit 6
    fi
  done
fi

echo
echo "城市样例数据生成完成："
META_FILE="${meta_file}" DIVERSITY_PROFILE="${diversity_profile}" HISTORY_MONTHS="${history_months}" DATA_DENSITY="${data_density}" GENERATION_MODE="${generation_mode}" CACHE_MODE="${amap_cache_mode}" CACHE_FILE="${amap_cache_file}" python3 <<'PY_SUMMARY'
import json
import os
from pathlib import Path
meta = json.loads(Path(os.environ['META_FILE']).read_text(encoding='utf-8'))
print(f"  生成模式：{os.environ['GENERATION_MODE']}")
print(f"  高德缓存：{os.environ['CACHE_MODE']} ({os.environ['CACHE_FILE']})")
print(f"  城市：{meta['city']}")
print(f"  地图中心：{meta['centerLongitude']},{meta['centerLatitude']}")
print(f"  真实小区 POI：{meta['communityCount']}")
print(f"  业务楼栋档案：{meta['buildingCount']}")
print(f"  其中高德真实楼栋 POI：{meta['realBuildingPoiCount']}")
print(f"  覆盖行政区：{'、'.join(meta['districts'])}")
print(f"  业务画像：{os.environ['DIVERSITY_PROFILE']}")
print(f"  历史跨度：{os.environ['HISTORY_MONTHS']} 个月")
print(f"  数据密度：{os.environ['DATA_DENSITY']}")
PY_SUMMARY

echo
echo "空间真实性说明："
echo "  - 小区中心、AOI 标识/面积来自高德开放平台；"
echo "  - 只有高德返回的单栋建筑 POI 才保存楼栋坐标；"
echo "  - 不生成并验证人工矩形小区/楼栋 Polygon；"
echo "  - 数据大屏的行政区边界和建筑楼块由高德官方图层绘制；"
echo "  - 社区画像、人口、楼龄、巡检、证据、上报等为固定种子的场景化业务数据；"
echo "  - 风险/优先级状态覆盖仅用于 SHOWCASE 展示楼栋，并在评分快照中显式标记。"
