#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
seed_script="${repository_root}/scripts/dev/seed-demo-knowledge.sh"

for required_file in "${env_file}" "${compose_file}" "${seed_script}"; do
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
  echo "[FAIL] PostgreSQL 服务未运行。" >&2
  exit 1
fi

run_sql() {
  local sql="$1"
  printf '%s\n' "${sql}" | "${compose[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -qAt -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
}

question_id=""
cleanup() {
  if [[ -n "${question_id}" ]]; then
    run_sql "DELETE FROM knowledge.citation WHERE question_id='${question_id}'; DELETE FROM knowledge.question WHERE id='${question_id}';" >/dev/null || true
  fi
}
trap cleanup EXIT

echo "[INFO] 首次写入演示知识……"
"${seed_script}" "${env_file}" >/dev/null

admin_id="$(run_sql "SELECT id FROM core.user_account WHERE username='demo_admin' AND deleted_at IS NULL LIMIT 1;")"
chunk_id_before="$(run_sql "SELECT c.id FROM knowledge.chunk c JOIN knowledge.document d ON d.id=c.document_id WHERE d.document_code='DEMO-KNOWLEDGE-CRACK-001' AND d.document_version='1.0.0' AND c.chunk_index=0;")"

if [[ -z "${admin_id}" || -z "${chunk_id_before}" ]]; then
  echo "[FAIL] 无法取得 demo_admin 或目标知识片段。" >&2
  exit 1
fi

question_id="$(run_sql "INSERT INTO knowledge.question (id, question_text, answer_text, evidence_sufficient, requested_by, request_context, status, created_at, answered_at) VALUES (gen_random_uuid(), '知识种子幂等性回归测试', '仅用于验证 citation 对 chunk 的引用稳定性', TRUE, '${admin_id}', '{\"test\":true}'::jsonb, 'ANSWERED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id;")"

run_sql "INSERT INTO knowledge.citation (id, question_id, chunk_id, citation_order, relevance_score, quoted_text, created_at) VALUES (gen_random_uuid(), '${question_id}', '${chunk_id_before}', 1, 1.0, '知识种子幂等性回归引用', CURRENT_TIMESTAMP);" >/dev/null

echo "[INFO] 已创建 citation，重复执行知识种子脚本……"
"${seed_script}" "${env_file}" >/dev/null

chunk_id_after="$(run_sql "SELECT c.id FROM knowledge.chunk c JOIN knowledge.document d ON d.id=c.document_id WHERE d.document_code='DEMO-KNOWLEDGE-CRACK-001' AND d.document_version='1.0.0' AND c.chunk_index=0;")"
citation_chunk_id="$(run_sql "SELECT chunk_id FROM knowledge.citation WHERE question_id='${question_id}' AND citation_order=1;")"
chunk_count="$(run_sql "SELECT count(*) FROM knowledge.chunk c JOIN knowledge.document d ON d.id=c.document_id WHERE d.document_code='DEMO-KNOWLEDGE-CRACK-001' AND d.document_version='1.0.0' AND c.chunk_index=0;")"

if [[ "${chunk_id_before}" != "${chunk_id_after}" ]]; then
  echo "[FAIL] 重复写入后 chunk UUID 发生变化：${chunk_id_before} -> ${chunk_id_after}" >&2
  exit 1
fi

if [[ "${citation_chunk_id}" != "${chunk_id_before}" ]]; then
  echo "[FAIL] citation 不再引用原知识片段。" >&2
  exit 1
fi

if [[ "${chunk_count}" != "1" ]]; then
  echo "[FAIL] 重复写入后目标知识片段数量异常：${chunk_count}" >&2
  exit 1
fi

echo "[PASS] 知识种子脚本可在已有 citation 的情况下重复执行，且 chunk UUID 保持稳定。"
