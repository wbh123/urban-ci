#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${repository_root}/.env"
compose_file="${repository_root}/docker/docker-compose.yml"
sql_file="${repository_root}/scripts/dev/repair-ai-detection-consistency.sql"
apply=false

usage() {
  cat <<'EOF'
用法：
  bash scripts/dev/repair-ai-detection-consistency.sh          # 只读 dry-run
  bash scripts/dev/repair-ai-detection-consistency.sh --apply  # 幂等补齐 REAL 检测投影

仅处理 mode=REAL 且 status=SUCCEEDED 的结果；统一检查 structured_result/raw_output_snapshot 中的模型原始检测集合与 ai.detection。
不会修改 MOCK；只有 --apply 才补齐缺失明细，也不会自动重算正式风险评分。
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply)
      apply=true
      shift
      ;;
    --env-file)
      [[ $# -ge 2 ]] || { echo "--env-file 缺少路径" >&2; exit 2; }
      env_file="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "未知参数：$1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

for required in "${env_file}" "${compose_file}" "${sql_file}"; do
  [[ -f "${required}" ]] || { echo "缺少必要文件：${required}" >&2; exit 1; }
done

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
if ! "${compose[@]}" ps --status running --services | grep -qx 'postgresql'; then
  echo "PostgreSQL 服务未运行。" >&2
  exit 1
fi

if [[ "${apply}" == "true" ]]; then
  echo "[APPLY] 将幂等补齐 REAL/SUCCEEDED 模型原始检测集合缺失的 ai.detection 明细。"
else
  echo "[DRY-RUN] 仅检查 REAL/SUCCEEDED 的模型原始检测快照与 ai.detection 数量差异。"
fi

"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -v apply="$1" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off' \
  sh "${apply}" < "${sql_file}"

if [[ "${apply}" == "false" ]]; then
  echo
  echo "只有 action=REPAIRABLE 或 REPAIRABLE_AND_FORMAL_ASSESSMENT_WILL_BE_STALED 时才适合考虑 --apply。"
  echo "出现 MANUAL_REVIEW* 时不要执行 --apply，应先人工核对该条记录。"
fi
