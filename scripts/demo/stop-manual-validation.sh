#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

KEEP_INFRA=false
if [[ "${1:-}" == "--keep-infra" ]]; then
  KEEP_INFRA=true
elif [[ -n "${1:-}" ]]; then
  fail "未知参数：$1（支持 --keep-infra）"
fi

stop_group_process frontend
stop_group_process backend
stop_group_process fastapi

if [[ "${KEEP_INFRA}" == "true" ]]; then
  info "保留 PostgreSQL / MinIO 运行"
else
  info "停止 PostgreSQL / MinIO（保留持久化数据目录）"
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" stop postgresql minio >/dev/null 2>&1 || true
fi

pass "手动验证环境已停止"
