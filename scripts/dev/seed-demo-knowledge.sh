#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
sql_file="${repository_root}/scripts/dev/seed-demo-knowledge.sql"

for required_file in "${env_file}" "${compose_file}" "${sql_file}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "[FAIL] 缺少必要文件：${required_file}" >&2
    exit 1
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "[FAIL] 未找到 docker 命令。" >&2
  exit 1
fi

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
if ! "${compose[@]}" ps --status running --services | grep -qx 'postgresql'; then
  echo "[FAIL] PostgreSQL 服务未运行，请先启动数据库。" >&2
  echo "       docker compose --env-file ${env_file} -f ${compose_file} up -d postgresql" >&2
  exit 1
fi

echo "[INFO] 写入经过审核标记的内部演示知识……"
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < "${sql_file}"

echo
echo "[PASS] 演示知识写入完成，可重复执行。"
echo "推荐在 AI 知识助手中演示以下问题："
echo "  1. 建筑外墙发现裂缝时，现场巡检应该重点记录哪些信息？"
echo "  2. 外墙饰面脱落整改完成后，复查复验应该重点检查什么？"
echo "  3. AI 可以直接修改正式风险评分吗？为什么？"
echo "  4. 处置整改和复查复验应该如何形成闭环？"
echo
echo "说明：这些知识明确标记为项目内部演示指南，不冒充国家标准或法定鉴定文件。"
