#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${repository_root}/.env}"
compose_file="${repository_root}/docker/docker-compose.yml"
base_sql_file="${repository_root}/scripts/dev/seed-demo-data.sql"
spatial_boundary_sql_file="${repository_root}/scripts/dev/seed-spatial-boundary-demo.sql"
feedback_sql_file="${repository_root}/scripts/dev/seed-feedback-demo-data.sql"
knowledge_sql_file="${repository_root}/scripts/dev/seed-demo-knowledge.sql"
assessment_input_sql_file="${repository_root}/scripts/dev/seed-assessment-input-data.sql"
assessment_calculate_script="${repository_root}/scripts/dev/calculate-assessment-demo.sh"
assessment_verify_sql_file="${repository_root}/scripts/dev/verify-assessment-demo.sql"

for required_file in \
  "${env_file}" \
  "${compose_file}" \
  "${base_sql_file}" \
  "${spatial_boundary_sql_file}" \
  "${feedback_sql_file}" \
  "${knowledge_sql_file}" \
  "${assessment_input_sql_file}" \
  "${assessment_calculate_script}" \
  "${assessment_verify_sql_file}"; do
  if [[ ! -f "${required_file}" ]]; then
    echo "缺少必要文件：${required_file}" >&2
    exit 1
  fi
done

if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 docker 命令。" >&2
  exit 1
fi

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")

if ! "${compose[@]}" ps --status running --services | grep -qx 'postgresql'; then
  echo "PostgreSQL 服务未运行，请先启动：" >&2
  echo "docker compose --env-file ${env_file} -f ${compose_file} up -d postgresql" >&2
  exit 1
fi

run_sql_file() {
  local sql_file="$1"
  echo "正在执行：${sql_file#${repository_root}/}"
  "${compose[@]}" exec -T postgresql sh -eu -c \
    'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    < "${sql_file}"
}

run_sql_file "${base_sql_file}"
run_sql_file "${spatial_boundary_sql_file}"
run_sql_file "${feedback_sql_file}"
run_sql_file "${knowledge_sql_file}"
run_sql_file "${assessment_input_sql_file}"
"${assessment_calculate_script}" "${env_file}"
run_sql_file "${assessment_verify_sql_file}"

echo
echo "测试账号："
printf '%-24s %-24s %s\n' '用户名' '密码' '角色'
printf '%-24s %-24s %s\n' 'demo_admin' 'UrbanSafe@123' '系统管理员'
printf '%-24s %-24s %s\n' 'demo_government' 'UrbanSafe@123' '住建部门管理人员'
printf '%-24s %-24s %s\n' 'demo_community' 'UrbanSafe@123' '街道社区管理人员'
printf '%-24s %-24s %s\n' 'demo_inspector' 'UrbanSafe@123' '物业巡检人员'
printf '%-24s %-24s %s\n' 'demo_expert' 'UrbanSafe@123' '专业复核人员'
printf '%-24s %-24s %s\n' 'demo_disposer' 'UrbanSafe@123' '问题处置人员'

echo
echo "公众反馈查询演示："
printf '%-28s %s\n' 'DEMO-FEEDBACK-WEB-001' 'demo-track-001'
printf '%-28s %s\n' 'DEMO-FEEDBACK-PHONE-001' 'demo-track-002'
printf '%-28s %s\n' 'DEMO-FEEDBACK-SMS-001' 'demo-track-003'

echo
echo "AI 知识问答演示："
printf '%-28s %s\n' '已审核内部知识' '6 份 ACTIVE 文档 / 11 个知识片段'
printf '%-28s %s\n' '推荐问题 1' '建筑外墙发现裂缝时，现场巡检应该重点记录哪些信息？'
printf '%-28s %s\n' '推荐问题 2' '处置整改和复查复验应该如何形成闭环？'
printf '%-28s %s\n' '推荐问题 3' 'AI 可以直接修改正式风险评分吗？为什么？'

echo
echo "第四阶段评分演示："
printf '%-12s %s\n' 'B-01' '高风险高完整度、经复核 REAL 证据、P1'
printf '%-12s %s\n' 'A-03' '高风险低完整度、仅 MOCK、缺少专业检测'
printf '%-12s %s\n' 'B-02' '中风险、重复公众反馈上限样例'
printf '%-12s %s\n' 'A-02' '低风险资料完整、稳定同分排序前项'
printf '%-12s %s\n' 'A-01' '缺少专业检测、稳定同分排序后项'

echo
echo "空间边界演示："
printf '%-28s %s\n' 'community_boundary' '2 个合成 VERIFIED 小区轮廓（仅开发演示）'
printf '%-28s %s\n' 'building_boundary' '5 个合成 VERIFIED 楼栋轮廓（仅开发演示）'

echo
echo "数据汇总："
"${compose[@]}" exec -T postgresql sh -eu -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -P pager=off' <<'SQL'
SELECT 'demo_users' AS item, count(*) AS total
FROM core.user_account
WHERE username LIKE 'demo\_%' ESCAPE '\' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_communities', count(*)
FROM core.community
WHERE community_code LIKE 'DEMO-%' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_buildings', count(*)
FROM core.building
WHERE remark LIKE 'DEMO_DATA%' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_community_boundaries', count(*)
FROM geo.community_boundary cb
JOIN core.community c ON c.id=cb.community_id
WHERE c.community_code LIKE 'DEMO-COMMUNITY-%'
  AND cb.deleted_at IS NULL AND cb.status='VERIFIED'
UNION ALL
SELECT 'demo_building_boundaries', count(*)
FROM geo.building_boundary bb
JOIN core.building b ON b.id=bb.building_id
WHERE b.remark LIKE 'DEMO_DATA%'
  AND bb.deleted_at IS NULL AND bb.status='VERIFIED'
UNION ALL
SELECT 'demo_tasks', count(*)
FROM core.inspection_task
WHERE task_code LIKE 'DEMO-TASK-%' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_records', count(*)
FROM core.inspection_record
WHERE remark LIKE 'DEMO_DATA%' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_feedback', count(*)
FROM core.resident_report
WHERE report_code LIKE 'DEMO-FEEDBACK-%' AND deleted_at IS NULL
UNION ALL
SELECT 'demo_feedback_events', count(*)
FROM core.resident_report_event events
JOIN core.resident_report reports ON reports.id=events.resident_report_id
WHERE reports.report_code LIKE 'DEMO-FEEDBACK-%'
UNION ALL
SELECT 'demo_knowledge_documents', count(*)
FROM knowledge.document
WHERE document_code LIKE 'DEMO-KNOWLEDGE-%' AND status='ACTIVE'
UNION ALL
SELECT 'demo_knowledge_chunks', count(*)
FROM knowledge.chunk c
JOIN knowledge.document d ON d.id=c.document_id
WHERE d.document_code LIKE 'DEMO-KNOWLEDGE-%' AND d.status='ACTIVE'
UNION ALL
SELECT 'demo_completeness_current', count(*)
FROM core.completeness_assessment
WHERE engine_version='phase4-rule-engine-v1' AND status='CURRENT'
UNION ALL
SELECT 'demo_risk_current', count(*)
FROM core.risk_assessment
WHERE engine_version='phase4-rule-engine-v1' AND status='CURRENT'
UNION ALL
SELECT 'demo_priority_current', count(*)
FROM core.renewal_priority
WHERE engine_version='phase4-rule-engine-v1'
  AND ranking_scope_key='ALL'
  AND status='CURRENT';
SQL

echo "测试数据写入完成。脚本可重复执行。"
