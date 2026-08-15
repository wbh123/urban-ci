#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TARGET="${ROOT_DIR}/scripts/dev/generate-showcase-data.sh"
CACHE_MODULE="${ROOT_DIR}/scripts/dev/showcase_amap_cache.py"
CACHE_RUNNER="${ROOT_DIR}/scripts/dev/generate-showcase-data-cached.py"
COVERAGE_SQL="${ROOT_DIR}/scripts/dev/ensure-showcase-status-coverage.sql"
CACHE_TEST="${ROOT_DIR}/scripts/dev/tests/test_showcase_amap_cache.py"

bash -n "${TARGET}"
python3 -m py_compile "${CACHE_MODULE}" "${CACHE_RUNNER}"
python3 "${CACHE_TEST}"

grep -Fq 'generation_mode="${SHOWCASE_GENERATION_MODE:-incremental}"' "${TARGET}"
grep -Fq -- '--mode clean' "${TARGET}"
grep -Fq -- '--mode incremental' "${TARGET}"
grep -Fq 'TRUNCATE TABLE core.community CASCADE;' "${TARGET}"
grep -Fq 'TRUNCATE TABLE geo.spatial_boundary_revision, geo.hazard_zone;' "${TARGET}"
grep -Fq 'if [[ "${generation_mode}" == "clean" ]]' "${TARGET}"
grep -Fq '[incremental] 保留现有地图、小区、楼栋与业务数据' "${TARGET}"

grep -Fq 'amap_cache_mode="${SHOWCASE_AMAP_CACHE_MODE:-prefer}"' "${TARGET}"
grep -Fq -- '--cache-mode' "${TARGET}"
grep -Fq -- '--cache-file' "${TARGET}"
grep -Fq 'SHOWCASE_AMAP_CACHE_MODE="${amap_cache_mode}"' "${TARGET}"
grep -Fq 'SHOWCASE_AMAP_CACHE_FILE="${amap_cache_file}"' "${TARGET}"
grep -Fq 'generate-showcase-data-cached.py' "${TARGET}"
grep -Fq 'data/showcase-cache/amap-query-cache-v1.json' "${TARGET}"

for level in LOW MEDIUM HIGH VERY_HIGH; do
  grep -Fq "'${level}'" "${COVERAGE_SQL}"
done
for level in P1 P2 P3 P4; do
  grep -Fq "'${level}'" "${COVERAGE_SQL}"
done
for freshness in CURRENT STALE NO_RESULT; do
  grep -Fq "${freshness}" "${COVERAGE_SQL}"
done
for task_status in PENDING IN_PROGRESS COMPLETED CANCELLED; do
  grep -Fq "t.status='${task_status}'" "${TARGET}"
done
for feedback_status in SUBMITTED ACCEPTED PROCESSING NEED_MORE_INFO RESOLVED CLOSED REJECTED CANCELLED; do
  grep -Fq "'${feedback_status}'" "${COVERAGE_SQL}"
done
grep -Fq "'showcaseCoverage', TRUE" "${COVERAGE_SQL}"
grep -Fq '公众反馈状态未全覆盖' "${COVERAGE_SQL}"
grep -Fq '核心展示状态未实现全覆盖' "${TARGET}"

for forbidden in \
  'TRUNCATE TABLE core.user_account' \
  'TRUNCATE TABLE core.role' \
  'TRUNCATE TABLE core.rule_version' \
  'TRUNCATE TABLE ai.model_registry'; do
  if grep -Fq "${forbidden}" "${TARGET}"; then
    echo "检测到禁止的 clean 清理语句：${forbidden}" >&2
    exit 1
  fi
done

if grep -Fq 'AMAP_KEY' "${ROOT_DIR}/data/showcase-cache/amap-query-cache-v1.json" 2>/dev/null; then
  echo "检测到缓存文件中出现 AMAP_KEY 字样。" >&2
  exit 1
fi

echo "展示数据 clean/incremental、缓存与状态覆盖脚本契约检查通过。"
