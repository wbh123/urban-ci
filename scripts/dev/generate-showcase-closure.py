#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import uuid
from pathlib import Path
from typing import Any


def q(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, (int, float)):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def jq(value: Any) -> str:
    return q(json.dumps(value, ensure_ascii=False, separators=(",", ":"))) + "::jsonb"


def asset_uuid(object_key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"urban-safe:showcase-asset:{object_key}"))


def generate_sql(
    manifest: dict[str, Any],
    bucket: str,
    history_months: int = 24,
    public_catalog: dict[str, Any] | None = None,
) -> str:
    assets = [item for item in (manifest.get("assets") or []) if isinstance(item, dict)]
    if not assets:
        raise ValueError("巡检图片 manifest 中没有 assets")
    issue_types = {str(item.get("issueType") or "") for item in assets}
    required_issues = {"CRACK", "WATER_LEAKAGE", "SURFACE_FALLING", "DEFORMATION"}
    missing = required_issues - issue_types
    if missing:
        raise ValueError(f"巡检图片缺少病害类型：{', '.join(sorted(missing))}")

    catalog = public_catalog or {"entries": []}
    entries = [item for item in (catalog.get("entries") or []) if isinstance(item, dict)]
    history_days = max(90, min(3650, int(history_months) * 30))

    sql: list[str] = [
        "BEGIN;",
        "-- 比赛完整闭环：空间底座保持真实，高频业务数据均为明确标记的演示模拟数据。",
        "CREATE OR REPLACE FUNCTION pg_temp.showcase_uuid(value text) RETURNS uuid LANGUAGE SQL IMMUTABLE AS $$",
        "  SELECT (substr(md5(value),1,8)||'-'||substr(md5(value),9,4)||'-'||substr(md5(value),13,4)||'-'||substr(md5(value),17,4)||'-'||substr(md5(value),21,12))::uuid",
        "$$;",
    ]

    for item in assets:
        object_key = str(item["objectKey"])
        metadata = {
            "showcaseGenerated": True,
            "syntheticImage": True,
            "issueType": item["issueType"],
            "variant": int(item.get("variant") or 1),
            "width": int(item.get("width") or 640),
            "height": int(item.get("height") or 360),
            "showcaseClosure": True,
        }
        sql.append(
            f"""
INSERT INTO asset.file_asset (
  id, bucket_name, object_key, original_filename, content_type, file_size, sha256,
  business_type, business_id, upload_status, uploaded_by, metadata, storage_provider
)
SELECT {q(asset_uuid(object_key))}::uuid, {q(bucket)}, {q(object_key)}, {q(item['filename'])},
       {q(item.get('contentType') or 'image/png')}, {int(item.get('size') or 0)}, {q(item['sha256'])},
       'SHOWCASE_ASSET', NULL, 'AVAILABLE', u.id, {jq(metadata)}, 'MINIO'
FROM core.user_account u
WHERE u.username='demo_inspector' AND u.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  bucket_name=EXCLUDED.bucket_name, object_key=EXCLUDED.object_key,
  original_filename=EXCLUDED.original_filename, content_type=EXCLUDED.content_type,
  file_size=EXCLUDED.file_size, sha256=EXCLUDED.sha256, upload_status='AVAILABLE',
  metadata=EXCLUDED.metadata, storage_provider='MINIO', updated_at=CURRENT_TIMESTAMP,
  deleted_at=NULL;
"""
        )

    # 将公开资料写入 extra_attributes，仅作为来源追溯；不把公开“改造”事实解释为现实风险。
    for entry in entries:
        name = str(entry.get("name") or "").strip()
        if not name:
            continue
        metadata = {
            "publicSourceVerified": True,
            "publicSourceName": entry.get("sourceTitle"),
            "publicSourceUrl": entry.get("sourceUrl"),
            "publicSourceTags": entry.get("tags") or [],
            "publicFacts": entry.get("publicFacts") or {},
            "publicSourceDisclaimer": "公开资料仅用于城市更新背景和来源追溯；病害、AI判断与风险等级为比赛模拟数据。",
        }
        normalized = name.replace("住宅小区", "").replace("小区", "")
        sql.append(
            f"""
UPDATE core.community c
SET extra_attributes = COALESCE(c.extra_attributes,'{{}}'::jsonb) || {jq(metadata)},
    updated_at = CURRENT_TIMESTAMP
WHERE c.deleted_at IS NULL
  AND c.community_code LIKE 'SHOWCASE-WH-%'
  AND regexp_replace(regexp_replace(c.community_name, '住宅小区$', ''), '小区$', '') = {q(normalized)};
"""
        )

    sql.append(
        f"""
DROP TABLE IF EXISTS tmp_showcase_buildings;
CREATE TEMP TABLE tmp_showcase_buildings ON COMMIT DROP AS
SELECT b.id AS building_id,
       b.building_code,
       b.building_name,
       b.community_id,
       c.community_code,
       c.community_name,
       b.construction_year,
       COALESCE(b.extra_attributes->>'scenarioProfile','MATURE_COMMODITY') AS scenario_profile,
       row_number() OVER (ORDER BY c.community_code,b.building_code,b.id) AS rn
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%';

CREATE INDEX ON tmp_showcase_buildings(building_id);

UPDATE core.building b
SET extra_attributes=COALESCE(b.extra_attributes,'{{}}'::jsonb) || jsonb_build_object(
      'showcaseClosure',true,
      'businessDataSynthetic',true,
      'minimumInspectionTasks',3,
      'minimumAiTasks',3,
      'historyMonths',{history_months}
    ),
    updated_at=CURRENT_TIMESTAMP
FROM tmp_showcase_buildings s
WHERE b.id=s.building_id;

-- 1. 每栋楼固定补齐 3 个已完成闭环巡检任务。原有历史任务继续保留用于状态多样性。
WITH expanded AS (
  SELECT s.*, gs AS seq,
         pg_temp.showcase_uuid(s.building_code || ':competition:task:' || gs::text) AS task_id,
         ('SHOWCASE-CLOSE-' || s.building_code || '-' || gs)::varchar(64) AS task_code,
         (7 + ((s.rn * 17 + gs * 41) % {history_days}))::int AS days_ago
  FROM tmp_showcase_buildings s
  CROSS JOIN LATERAL generate_series(1, 3) gs
)
INSERT INTO core.inspection_task (
  id, task_code, building_id, inspection_type, planned_at, assigned_to, focus_parts,
  status, created_by, title, description, started_at, completed_at, remark
)
SELECT e.task_id, e.task_code, e.building_id,
       CASE e.seq WHEN 1 THEN 'ROUTINE' WHEN 2 THEN 'SPECIAL' ELSE 'COMPREHENSIVE' END,
       CURRENT_TIMESTAMP - make_interval(days => e.days_ago), inspector.id,
       to_jsonb(ARRAY[CASE e.seq WHEN 1 THEN '外立面' WHEN 2 THEN '屋面及防水' ELSE '楼梯间及公共区域' END]),
       'COMPLETED', manager.id,
       e.building_name || CASE e.seq WHEN 1 THEN ' 例行安全巡检' WHEN 2 THEN ' 病害专项复查' ELSE ' 治理闭环复巡' END,
       CASE e.seq
         WHEN 1 THEN '建立表观病害基线并采集可供 AI 视觉分析的现场证据。'
         WHEN 2 THEN '结合前次巡检与居民反馈对重点部位进行专项复查。'
         ELSE '治理措施实施后进行闭环复巡，确认后续观察重点。' END,
       CURRENT_TIMESTAMP - make_interval(days => e.days_ago) + INTERVAL '20 minutes',
       CURRENT_TIMESTAMP - make_interval(days => e.days_ago) + INTERVAL '2 hours',
       '比赛演示完整闭环；现场问题与处置均为模拟数据'
FROM expanded e
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  status='COMPLETED', planned_at=EXCLUDED.planned_at, started_at=EXCLUDED.started_at,
  completed_at=EXCLUDED.completed_at, focus_parts=EXCLUDED.focus_parts,
  title=EXCLUDED.title, description=EXCLUDED.description, remark=EXCLUDED.remark,
  updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 2. 每个闭环任务形成一条现场记录。病害与楼龄/场景画像相关，而不是完全独立随机。
WITH expanded AS (
  SELECT s.*, gs AS seq,
         pg_temp.showcase_uuid(s.building_code || ':competition:task:' || gs::text) AS task_id,
         pg_temp.showcase_uuid(s.building_code || ':competition:record:' || gs::text) AS record_id,
         (7 + ((s.rn * 17 + gs * 41) % {history_days}))::int AS days_ago,
         CASE ((s.rn + gs) % 4)
           WHEN 0 THEN 'CRACK'
           WHEN 1 THEN 'WATER_LEAKAGE'
           WHEN 2 THEN 'SURFACE_FALLING'
           ELSE 'DEFORMATION' END AS issue_type
  FROM tmp_showcase_buildings s
  CROSS JOIN LATERAL generate_series(1, 3) gs
), prepared AS (
  SELECT e.*,
         CASE
           WHEN e.construction_year IS NOT NULL AND e.construction_year <= 1985 AND e.seq <= 2 THEN 'HIGH'
           WHEN e.construction_year IS NOT NULL AND e.construction_year <= 2003 THEN 'MEDIUM'
           WHEN e.scenario_profile IN ('OLD_CITY_FOCUS','OLD_WORK_UNIT') AND e.seq=2 THEN 'MEDIUM'
           ELSE 'LOW' END AS severity
  FROM expanded e
)
INSERT INTO core.inspection_record (
  id, inspection_task_id, building_id, inspector_id, inspection_part,
  inspected_at, submitted_at, status, summary, form_data, issue_type,
  severity, rectification_suggestion, extra_data, remark
)
SELECT p.record_id, p.task_id, p.building_id, inspector.id,
       CASE p.issue_type WHEN 'CRACK' THEN '外立面' WHEN 'WATER_LEAKAGE' THEN '屋面及外墙'
            WHEN 'SURFACE_FALLING' THEN '外墙饰面' ELSE '公共构件' END,
       CURRENT_TIMESTAMP - make_interval(days => p.days_ago) + INTERVAL '40 minutes',
       CURRENT_TIMESTAMP - make_interval(days => p.days_ago) + INTERVAL '90 minutes',
       'COMPLETED',
       CASE p.issue_type
         WHEN 'CRACK' THEN '发现局部裂缝或修补痕迹，已记录位置并进入 AI 视觉辅助识别。'
         WHEN 'WATER_LEAKAGE' THEN '发现雨水渗漏或潮湿痕迹，重点核查屋面和外墙防水节点。'
         WHEN 'SURFACE_FALLING' THEN '发现饰面老化、空鼓或局部脱落迹象，已设置维护关注。'
         ELSE '发现局部构件线形或表面异常，进入专业复核与持续观察。' END,
       jsonb_build_object(
          'showcaseGenerated',true,'showcaseClosure',true,'businessDataSynthetic',true,
          'photoCount',1,'weather',CASE p.rn%4 WHEN 0 THEN 'RAIN_AFTER' WHEN 1 THEN 'CLOUDY' WHEN 2 THEN 'SUNNY' ELSE 'OVERCAST' END,
          'inspectionSequence',p.seq
       ),
       p.issue_type, p.severity,
       CASE p.severity WHEN 'HIGH' THEN '建议尽快安排专业复核，并结合历史记录确定维修和复巡计划。'
            WHEN 'MEDIUM' THEN '建议纳入近期维护计划并在下次巡检中复测。'
            ELSE '记录基线并持续观察，按计划开展常规维护。' END,
       jsonb_build_object(
          'showcaseGenerated',true,'showcaseClosure',true,'businessDataSynthetic',true,
          'requiresReview',(p.seq=1),'source','COMPETITION_SYNTHETIC_INSPECTION'
       ),
       '比赛演示巡检记录，病害内容为模拟数据'
FROM prepared p
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  inspection_task_id=EXCLUDED.inspection_task_id, building_id=EXCLUDED.building_id,
  inspector_id=EXCLUDED.inspector_id, inspection_part=EXCLUDED.inspection_part,
  inspected_at=EXCLUDED.inspected_at, submitted_at=EXCLUDED.submitted_at,
  status='COMPLETED', summary=EXCLUDED.summary, form_data=EXCLUDED.form_data,
  issue_type=EXCLUDED.issue_type, severity=EXCLUDED.severity,
  rectification_suggestion=EXCLUDED.rectification_suggestion,
  extra_data=EXCLUDED.extra_data, remark=EXCLUDED.remark,
  updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 3. 为每种病害建立可复用的本地演示图片索引，并绑定到每条闭环巡检记录。
DROP TABLE IF EXISTS tmp_showcase_assets;
CREATE TEMP TABLE tmp_showcase_assets ON COMMIT DROP AS
SELECT fa.id AS asset_id,
       fa.metadata->>'issueType' AS issue_type,
       COALESCE((fa.metadata->>'variant')::int,1) AS variant,
       row_number() OVER (PARTITION BY fa.metadata->>'issueType' ORDER BY COALESCE((fa.metadata->>'variant')::int,1),fa.id) AS asset_rank,
       count(*) OVER (PARTITION BY fa.metadata->>'issueType') AS asset_count
FROM asset.file_asset fa
WHERE fa.deleted_at IS NULL
  AND COALESCE(fa.metadata->>'showcaseClosure','false')='true'
  AND COALESCE(fa.metadata->>'syntheticImage','false')='true';

WITH records AS (
  SELECT r.id AS record_id, r.issue_type,
         s.rn,
         (regexp_match(t.task_code, '-([123])$'))[1]::int AS seq
  FROM core.inspection_record r
  JOIN core.inspection_task t ON t.id=r.inspection_task_id AND t.deleted_at IS NULL
  JOIN tmp_showcase_buildings s ON s.building_id=r.building_id
  WHERE r.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%'
), chosen AS (
  SELECT r.*, a.asset_id,
         row_number() OVER (PARTITION BY r.record_id ORDER BY a.asset_rank) AS choice_rank
  FROM records r
  JOIN tmp_showcase_assets a ON a.issue_type=r.issue_type
  WHERE a.asset_rank = 1 + ((r.rn + r.seq - 2) % a.asset_count)
)
INSERT INTO asset.asset_binding (
  id, asset_id, business_type, business_id, binding_role
)
SELECT pg_temp.showcase_uuid('asset-binding:' || c.record_id::text), c.asset_id,
       'INSPECTION_RECORD', c.record_id, 'PHOTO'
FROM chosen c
WHERE c.choice_rank=1
ON CONFLICT (id) DO UPDATE SET
  asset_id=EXCLUDED.asset_id, business_type=EXCLUDED.business_type,
  business_id=EXCLUDED.business_id, binding_role=EXCLUDED.binding_role,
  updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 4. 每栋楼至少两条治理证据：专业复核依据 + 维修/复巡闭环。
WITH expanded AS (
  SELECT s.*, gs AS seq,
         pg_temp.showcase_uuid(s.building_code || ':competition:evidence:' || gs::text) AS evidence_id
  FROM tmp_showcase_buildings s
  CROSS JOIN LATERAL generate_series(1, 2) gs
)
INSERT INTO core.building_evidence (
  id, building_id, evidence_type, title, description, occurred_at,
  source, reliability_level, evidence_data, created_by
)
SELECT e.evidence_id, e.building_id,
       CASE e.seq WHEN 1 THEN 'PROFESSIONAL_INSPECTION' ELSE 'MAINTENANCE_RECORD' END,
       CASE e.seq WHEN 1 THEN 'AI 发现后的专业复核依据' ELSE '治理处置与复巡跟踪记录' END,
       CASE e.seq
         WHEN 1 THEN '结合巡检图像、AI 视觉发现和历史档案进行人工专业复核，形成后续风险评估输入。'
         ELSE '根据复核意见安排维护或持续观察，并通过后续巡检确认治理状态。' END,
       CURRENT_TIMESTAMP - make_interval(days => CASE e.seq WHEN 1 THEN 6 + (e.rn%150) ELSE 2 + (e.rn%90) END),
       CASE e.seq WHEN 1 THEN '比赛演示专业复核台账' ELSE '比赛演示社区治理台账' END,
       CASE e.seq WHEN 1 THEN 'PROFESSIONAL_CONFIRMED' ELSE 'OFFICIAL_RECORD' END,
       jsonb_build_object(
         'showcaseGenerated',true,'showcaseClosure',true,'businessDataSynthetic',true,
         'stage',CASE e.seq WHEN 1 THEN 'HUMAN_REVIEW' ELSE 'RECTIFICATION_FOLLOW_UP' END,
         'recommendedNextStep',CASE e.seq WHEN 1 THEN '风险评分与治理排序' ELSE '按周期复巡' END
       ), manager.id
FROM expanded e
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  evidence_type=EXCLUDED.evidence_type, title=EXCLUDED.title,
  description=EXCLUDED.description, occurred_at=EXCLUDED.occurred_at,
  source=EXCLUDED.source, reliability_level=EXCLUDED.reliability_level,
  evidence_data=EXCLUDED.evidence_data, created_by=EXCLUDED.created_by,
  updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;

-- 5. 每栋楼至少一条居民线索，形成“居民反馈 → 巡检 → AI → 复核”入口。
INSERT INTO core.resident_report (
  report_code, community_id, building_id, reporter_user_id, report_type,
  description, status, urgency, evidence, submitted_at, handled_at
)
SELECT ('SHOWCASE-CLOSE-REPORT-' || s.building_code)::varchar(64),
       s.community_id, s.building_id, reporter.id,
       CASE s.rn%4 WHEN 0 THEN 'WALL_CRACK' WHEN 1 THEN 'WATER_LEAKAGE'
            WHEN 2 THEN 'SURFACE_FALLING' ELSE 'DEFORMATION' END,
       '居民提交建筑公共部位异常线索，系统已关联后续巡检、AI 识别和人工复核记录。',
       CASE s.rn%8 WHEN 0 THEN 'PROCESSING' WHEN 1 THEN 'ACCEPTED'
            WHEN 2 THEN 'CLOSED' ELSE 'RESOLVED' END,
       CASE WHEN s.construction_year IS NOT NULL AND s.construction_year<=1985 THEN 'HIGH'
            WHEN s.rn%11=0 THEN 'URGENT' ELSE 'NORMAL' END,
       jsonb_build_array(jsonb_build_object(
          'type','SHOWCASE_TEXT','showcaseGenerated',true,'showcaseClosure',true,
          'note','演示居民反馈，不对应真实居民或现实安全事件'
       )),
       CURRENT_TIMESTAMP - make_interval(days => 10 + (s.rn%180)),
       CASE WHEN s.rn%8 IN (0,1) THEN NULL ELSE CURRENT_TIMESTAMP - make_interval(days => 3 + (s.rn%60)) END
FROM tmp_showcase_buildings s
JOIN core.user_account reporter ON reporter.username='demo_inspector' AND reporter.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
  community_id=EXCLUDED.community_id, building_id=EXCLUDED.building_id,
  reporter_user_id=EXCLUDED.reporter_user_id, report_type=EXCLUDED.report_type,
  description=EXCLUDED.description, status=EXCLUDED.status, urgency=EXCLUDED.urgency,
  evidence=EXCLUDED.evidence, submitted_at=EXCLUDED.submitted_at,
  handled_at=EXCLUDED.handled_at, updated_at=CURRENT_TIMESTAMP;

-- 6. 每条闭环巡检图片创建 AI 推理任务；第一、二次稳定成功，第三次用于实时状态分布。
WITH base AS (
  SELECT s.*, r.id AS record_id, t.id AS inspection_task_id,
         (regexp_match(t.task_code, '-([123])$'))[1]::int AS seq,
         ab.asset_id,
         r.issue_type,
         r.severity
  FROM tmp_showcase_buildings s
  JOIN core.inspection_task t ON t.building_id=s.building_id AND t.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%'
  JOIN core.inspection_record r ON r.inspection_task_id=t.id AND r.deleted_at IS NULL
  JOIN asset.asset_binding ab ON ab.business_type='INSPECTION_RECORD' AND ab.business_id=r.id AND ab.deleted_at IS NULL AND ab.binding_role='PHOTO'
), prepared AS (
  SELECT b.*,
         ('SHOWCASE-AI-' || b.building_code || '-' || b.seq)::varchar(64) AS request_code,
         CASE
           WHEN b.seq IN (1,2) THEN 'SUCCEEDED'
           WHEN b.rn%10=0 THEN 'PENDING'
           WHEN b.rn%10=1 THEN 'RUNNING'
           WHEN b.rn%10=2 THEN 'FAILED'
           ELSE 'SUCCEEDED' END AS ai_status,
         CASE WHEN b.seq=3 THEN CURRENT_TIMESTAMP - make_interval(hours => 1 + (b.rn%23))
              ELSE CURRENT_TIMESTAMP - make_interval(days => 4 + ((b.rn*13+b.seq*7)%{history_days})) END AS requested_at
  FROM base b
)
INSERT INTO ai.inference_task (
  id, request_code, idempotency_key, asset_id, inspection_task_id, inspection_record_id,
  building_id, community_id, model_registry_id, mode, status, attempt_no, review_status,
  requested_by, requested_at, started_at, completed_at, duration_ms, error_code, error_message
)
SELECT pg_temp.showcase_uuid('ai-task:' || p.request_code), p.request_code,
       ('showcase:' || p.building_code || ':' || p.seq)::varchar(128),
       p.asset_id, p.inspection_task_id, p.record_id, p.building_id, p.community_id,
       model.id, 'MOCK', p.ai_status, 1,
       CASE WHEN p.seq=1 THEN CASE p.rn%3 WHEN 0 THEN 'CONFIRMED' WHEN 1 THEN 'CORRECTED' ELSE 'REJECTED' END ELSE 'UNREVIEWED' END,
       inspector.id, p.requested_at,
       CASE WHEN p.ai_status='PENDING' THEN NULL ELSE p.requested_at + INTERVAL '20 seconds' END,
       CASE WHEN p.ai_status IN ('SUCCEEDED','FAILED') THEN p.requested_at + INTERVAL '55 seconds' ELSE NULL END,
       CASE WHEN p.ai_status='SUCCEEDED' THEN 350 + ((p.rn*31+p.seq*17)%2600) ELSE NULL END,
       CASE WHEN p.ai_status='FAILED' THEN 'SHOWCASE_TRANSIENT_INFERENCE_FAILURE' ELSE NULL END,
       CASE WHEN p.ai_status='FAILED' THEN '演示任务模拟一次可重试失败，用于展示异常状态与人工兜底。' ELSE NULL END
FROM prepared p
JOIN ai.model_registry model ON model.model_code='AI-DEFECT-MOCK-001' AND model.deleted_at IS NULL
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  asset_id=EXCLUDED.asset_id, inspection_task_id=EXCLUDED.inspection_task_id,
  inspection_record_id=EXCLUDED.inspection_record_id, building_id=EXCLUDED.building_id,
  community_id=EXCLUDED.community_id, model_registry_id=EXCLUDED.model_registry_id,
  mode=EXCLUDED.mode, status=EXCLUDED.status, attempt_no=EXCLUDED.attempt_no,
  review_status=EXCLUDED.review_status, requested_by=EXCLUDED.requested_by,
  requested_at=EXCLUDED.requested_at, started_at=EXCLUDED.started_at,
  completed_at=EXCLUDED.completed_at, duration_ms=EXCLUDED.duration_ms,
  error_code=EXCLUDED.error_code, error_message=EXCLUDED.error_message,
  updated_at=CURRENT_TIMESTAMP;

-- 7. 成功 AI 任务生成标准结果，并在 raw snapshot 中保留演示声明与丰富几何。
INSERT INTO ai.inference_result (
  id, inference_task_id, image_width, image_height, quality_status, applicability,
  summary, raw_output_snapshot, warning_messages, result_asset_id
)
SELECT pg_temp.showcase_uuid('ai-result:' || t.request_code), t.id, 640, 360,
       'GOOD', 'APPLICABLE',
       jsonb_build_object(
          'showcaseGenerated',true,'showcaseClosure',true,'businessDataSynthetic',true,
          'finding','AI 视觉发现疑似表观病害，需结合人工复核和正式风险评估解释。',
          'detectionCount',1 + (s.rn%3),
          'confidenceBand',CASE s.rn%4 WHEN 0 THEN 'LOW_DISPLAY_HIDDEN' WHEN 1 THEN 'MEDIUM' WHEN 2 THEN 'HIGH' ELSE 'VERY_HIGH' END
       ),
       jsonb_build_object(
          'provider','SHOWCASE_SYNTHETIC','showcaseGenerated',true,'businessDataSynthetic',true,
          'modelRole','AI_VISUAL_ASSISTANCE',
          'disclaimer','演示 AI 结果，不代表对应真实建筑存在病害或安全风险',
          'geometryType','BBOX_AND_POLYGON'
       ),
       jsonb_build_array('AI 识别仅用于演示辅助决策链路，人工结论优先'),
       NULL
FROM ai.inference_task t
JOIN tmp_showcase_buildings s ON s.building_id=t.building_id
WHERE t.request_code LIKE 'SHOWCASE-AI-%' AND t.status='SUCCEEDED'
ON CONFLICT (inference_task_id) DO UPDATE SET
  image_width=EXCLUDED.image_width, image_height=EXCLUDED.image_height,
  quality_status=EXCLUDED.quality_status, applicability=EXCLUDED.applicability,
  summary=EXCLUDED.summary, raw_output_snapshot=EXCLUDED.raw_output_snapshot,
  warning_messages=EXCLUDED.warning_messages;

-- 8. 每个成功结果生成 1~3 个 detection，同时写 bbox 和 polygon。
WITH results AS (
  SELECT ir.id AS result_id, t.request_code, t.inspection_record_id, s.rn,
         r.issue_type, r.severity,
         (regexp_match(t.request_code, '-([123])$'))[1]::int AS seq
  FROM ai.inference_result ir
  JOIN ai.inference_task t ON t.id=ir.inference_task_id
  JOIN tmp_showcase_buildings s ON s.building_id=t.building_id
  JOIN core.inspection_record r ON r.id=t.inspection_record_id
  WHERE t.request_code LIKE 'SHOWCASE-AI-%' AND t.status='SUCCEEDED'
), expanded AS (
  SELECT r.*, gs AS detection_seq
  FROM results r
  CROSS JOIN LATERAL generate_series(1, 1 + (r.rn%3)) gs
)
INSERT INTO ai.detection (
  id, inference_result_id, sequence_no, class_code, class_name, confidence,
  bbox_x, bbox_y, bbox_width, bbox_height, coordinate_type, extra_data
)
SELECT pg_temp.showcase_uuid('detection:' || e.request_code || ':' || e.detection_seq),
       e.result_id, e.detection_seq, e.issue_type,
       CASE e.issue_type WHEN 'CRACK' THEN '裂缝' WHEN 'WATER_LEAKAGE' THEN '渗水'
            WHEN 'SURFACE_FALLING' THEN '表面脱落' ELSE '变形' END,
       round((0.33 + ((e.rn*11 + e.seq*7 + e.detection_seq*13)%61)/100.0)::numeric,5),
       round((0.10 + (e.detection_seq-1)*0.13)::numeric,5),
       round((0.16 + ((e.rn+e.detection_seq)%3)*0.09)::numeric,5),
       0.28, 0.30, 'NORMALIZED_XYWH',
       jsonb_build_object(
          'showcaseGenerated',true,'showcaseClosure',true,'businessDataSynthetic',true,
          'severity',e.severity,
          'polygon',jsonb_build_array(
             jsonb_build_array(round((0.10 + (e.detection_seq-1)*0.13)::numeric,5), round((0.16 + ((e.rn+e.detection_seq)%3)*0.09)::numeric,5)),
             jsonb_build_array(round((0.34 + (e.detection_seq-1)*0.13)::numeric,5), round((0.18 + ((e.rn+e.detection_seq)%3)*0.09)::numeric,5)),
             jsonb_build_array(round((0.36 + (e.detection_seq-1)*0.13)::numeric,5), round((0.42 + ((e.rn+e.detection_seq)%3)*0.09)::numeric,5)),
             jsonb_build_array(round((0.12 + (e.detection_seq-1)*0.13)::numeric,5), round((0.44 + ((e.rn+e.detection_seq)%3)*0.09)::numeric,5))
          ),
          'geometryType','POLYGON'
       )
FROM expanded e
ON CONFLICT (id) DO UPDATE SET
  class_code=EXCLUDED.class_code, class_name=EXCLUDED.class_name,
  confidence=EXCLUDED.confidence, bbox_x=EXCLUDED.bbox_x, bbox_y=EXCLUDED.bbox_y,
  bbox_width=EXCLUDED.bbox_width, bbox_height=EXCLUDED.bbox_height,
  coordinate_type=EXCLUDED.coordinate_type, extra_data=EXCLUDED.extra_data;

-- 9. 第一轮 AI 任务每栋楼均进入人工复核；确认、修正、驳回三类结论均有样本。
INSERT INTO ai.inference_review (
  id, inference_task_id, review_status, review_comment, reviewed_by,
  reviewed_at, corrected_data
)
SELECT pg_temp.showcase_uuid('ai-review:' || t.request_code), t.id,
       CASE s.rn%3 WHEN 0 THEN 'CONFIRMED' WHEN 1 THEN 'CORRECTED' ELSE 'REJECTED' END,
       CASE s.rn%3
         WHEN 0 THEN '人工复核确认该 AI 发现可作为后续风险评估的辅助证据。'
         WHEN 1 THEN '人工复核修正了病害范围/严重程度，正式业务以人工结论为准。'
         ELSE '人工复核认为该低可信 AI 发现不成立，不进入正式风险结论。' END,
       reviewer.id,
       COALESCE(t.completed_at,t.requested_at) + INTERVAL '45 minutes',
       CASE s.rn%3 WHEN 1 THEN jsonb_build_object('showcaseGenerated',true,'correctedSeverity','MEDIUM','humanOverride',true)
            ELSE jsonb_build_object('showcaseGenerated',true,'humanOverride',true) END
FROM ai.inference_task t
JOIN tmp_showcase_buildings s ON s.building_id=t.building_id
JOIN core.user_account reviewer ON reviewer.username='demo_community' AND reviewer.deleted_at IS NULL
WHERE t.request_code=('SHOWCASE-AI-' || s.building_code || '-1') AND t.status='SUCCEEDED'
ON CONFLICT (id) DO UPDATE SET
  review_status=EXCLUDED.review_status, review_comment=EXCLUDED.review_comment,
  reviewed_by=EXCLUDED.reviewed_by, reviewed_at=EXCLUDED.reviewed_at,
  corrected_data=EXCLUDED.corrected_data;

-- 10. SQL 内部最低闭环检查：在正式风险评分前，所有楼栋必须已有巡检/反馈/AI/复核/治理证据。
DO $$
DECLARE missing_count integer;
BEGIN
  SELECT count(*) INTO missing_count
  FROM tmp_showcase_buildings s
  WHERE (SELECT count(*) FROM core.inspection_task t WHERE t.building_id=s.building_id AND t.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%') < 3
     OR (SELECT count(*) FROM core.inspection_record r JOIN core.inspection_task t ON t.id=r.inspection_task_id WHERE r.building_id=s.building_id AND r.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%') < 3
     OR (SELECT count(*) FROM core.building_evidence e WHERE e.building_id=s.building_id AND e.deleted_at IS NULL AND COALESCE(e.evidence_data->>'showcaseClosure','false')='true') < 2
     OR (SELECT count(*) FROM core.resident_report rr WHERE rr.building_id=s.building_id AND rr.deleted_at IS NULL AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%') < 1
     OR (SELECT count(*) FROM ai.inference_task ait WHERE ait.building_id=s.building_id AND ait.request_code LIKE 'SHOWCASE-AI-%') < 3
     OR (SELECT count(*) FROM ai.inference_task ait WHERE ait.building_id=s.building_id AND ait.request_code LIKE 'SHOWCASE-AI-%' AND ait.status='SUCCEEDED') < 2
     OR (SELECT count(*) FROM ai.inference_review air JOIN ai.inference_task ait ON ait.id=air.inference_task_id WHERE ait.building_id=s.building_id AND ait.request_code LIKE 'SHOWCASE-AI-%') < 1;
  IF missing_count > 0 THEN
    RAISE EXCEPTION '比赛闭环生成失败：% 栋楼未达到风险评分前的最低巡检/AI/复核/治理覆盖', missing_count;
  END IF;
  RAISE NOTICE '风险评分前闭环检查通过：% 栋楼均具备完整巡检/AI/人工复核/治理输入', (SELECT count(*) FROM tmp_showcase_buildings);
END $$;

COMMIT;
"""
    )
    return "\n".join(sql)


def main(argv: list[str]) -> int:
    manifest_path = Path(os.environ.get("SHOWCASE_ASSET_MANIFEST", argv[1] if len(argv) > 1 else "data/showcase-assets/inspection/manifest.json"))
    output_path = Path(os.environ.get("SHOWCASE_CLOSURE_SQL_FILE", argv[2] if len(argv) > 2 else "showcase-closure.sql"))
    catalog_path = Path(os.environ.get("SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE", "data/showcase-sources/wuhan-old-community-catalog-v1.json"))
    bucket = os.environ.get("SHOWCASE_ASSET_BUCKET", "").strip()
    if not bucket:
        raise SystemExit("SHOWCASE_ASSET_BUCKET 为空")
    history_months = int(os.environ.get("SHOWCASE_HISTORY_MONTHS", "24"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    catalog = json.loads(catalog_path.read_text(encoding="utf-8")) if catalog_path.exists() else {"entries": []}
    sql = generate_sql(manifest, bucket, history_months=history_months, public_catalog=catalog)
    output_path.write_text(sql, encoding="utf-8")
    print(
        f"比赛闭环 SQL 已生成：{output_path}，assets={len(manifest.get('assets') or [])}，historyMonths={history_months}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
