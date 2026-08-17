#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
sql_file="${repository_root}/scripts/dev/mark-showcase-golden-buildings.sql"

for required in "${env_file}" "${compose_file}" "${sql_file}"; do
  if [[ ! -f "${required}" ]]; then
    echo "缺少必要文件：${required}" >&2
    exit 1
  fi
done

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
if ! "${compose[@]}" ps --status running --services | grep -qx 'postgresql'; then
  echo "PostgreSQL 服务未运行。" >&2
  exit 1
fi

echo "正在准备比赛主演示智能工作流治理开关与 Dify Review Assist..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off' <<'SQL_AI_SETTINGS'
INSERT INTO ai.governance_setting (setting_key, boolean_value, updated_by, updated_at)
VALUES ('INTELLIGENT_WORKFLOW_ENABLED', TRUE, NULL, CURRENT_TIMESTAMP)
ON CONFLICT (setting_key) DO UPDATE
SET boolean_value=TRUE,
    updated_by=NULL,
    updated_at=CURRENT_TIMESTAMP;

UPDATE ai.workflow_definition
SET enabled=TRUE,
    quality_status=CASE WHEN quality_status='PLANNED' THEN 'VALIDATING' ELSE quality_status END,
    current_version=CASE
        WHEN current_version IS NULL OR btrim(current_version)='' THEN 'review-assist-v1.0.0'
        ELSE current_version
    END,
    input_schema_version=COALESCE(NULLIF(btrim(input_schema_version), ''), '1.0'),
    output_schema_version=COALESCE(NULLIF(btrim(output_schema_version), ''), '1.0'),
    formal_evidence_enabled=FALSE,
    updated_at=CURRENT_TIMESTAMP
WHERE workflow_code='DIFY-REVIEW-ASSIST-001';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM ai.workflow_definition
    WHERE workflow_code='DIFY-REVIEW-ASSIST-001'
  ) THEN
    RAISE EXCEPTION 'DIFY-REVIEW-ASSIST-001 未登记；请确认 Flyway V31/V41 已执行并重启后端';
  END IF;
END $$;

SELECT setting_key, boolean_value, updated_at
FROM ai.governance_setting
WHERE setting_key='INTELLIGENT_WORKFLOW_ENABLED';

SELECT workflow_code, enabled, quality_status, current_version, formal_evidence_enabled, updated_at
FROM ai.workflow_definition
WHERE workflow_code='DIFY-REVIEW-ASSIST-001';
SQL_AI_SETTINGS

echo
echo "正在选择比赛主演示黄金楼栋..."
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off' \
  < "${sql_file}"

echo
echo "比赛主演示准备完成：智能工作流治理开关与 Dify Review Assist 已显式启用，黄金楼栋已重新选择。"
echo "has_real_ai=false 不是失败：它表示该楼栋尚未产生真实视觉推理，preflight 会单独强校验并给出明确提示。"
