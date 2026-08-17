-- 比赛运营数据增强：补齐 AI 结构化结果、降级审计，以及真实可操作的整改→复查复验时间线。

BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.showcase_uuid(value text) RETURNS uuid LANGUAGE SQL IMMUTABLE AS $$
  SELECT (substr(md5(value),1,8)||'-'||substr(md5(value),9,4)||'-'||substr(md5(value),13,4)||'-'||substr(md5(value),17,4)||'-'||substr(md5(value),21,12))::uuid
$$;

-- AI 结构化结果：直接服务 AI 工作台、楼栋 AI 发现和人工复核页。
UPDATE ai.inference_result ir
SET structured_result = jsonb_build_object(
      'showcaseGenerated',true,
      'businessDataSynthetic',true,
      'summary',COALESCE(ir.summary->>'finding','AI 视觉发现已完成'),
      'confidenceBand',COALESCE(ir.summary->>'confidenceBand','MEDIUM'),
      'requiresHumanReview',true,
      'detections',COALESCE((
        SELECT jsonb_agg(jsonb_build_object(
          'classCode',d.class_code,
          'className',d.class_name,
          'confidence',d.confidence,
          'bbox',jsonb_build_object('x',d.bbox_x,'y',d.bbox_y,'width',d.bbox_width,'height',d.bbox_height),
          'polygon',d.extra_data->'polygon',
          'severity',d.extra_data->>'severity'
        ) ORDER BY d.sequence_no)
        FROM ai.detection d WHERE d.inference_result_id=ir.id
      ),'[]'::jsonb),
      'recommendedActions',jsonb_build_array('人工复核','结合巡检与档案进行风险评分','按风险等级安排维修或复巡'),
      'disclaimer','比赛演示 AI 数据，不代表对应真实建筑现实安全状况'
    ),
    raw_response_reference=('showcase://' || t.request_code)
FROM ai.inference_task t
JOIN core.community c ON c.id=t.community_id
WHERE ir.inference_task_id=t.id
  AND c.community_code LIKE 'SHOWCASE-WH-%'
  AND t.request_code LIKE 'SHOWCASE-AI-%';

-- 少量近期成功任务模拟“在线工作流不可用后自动降级本地视觉能力”，便于人工智能运行状态页面展示。
WITH ranked AS (
  SELECT t.id,
         row_number() OVER (ORDER BY t.requested_at DESC,t.id) AS rn
  FROM ai.inference_task t
  JOIN core.community c ON c.id=t.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND t.request_code LIKE 'SHOWCASE-AI-%-3'
    AND t.status='SUCCEEDED'
)
UPDATE ai.inference_task t
SET provider_code=CASE WHEN r.rn%12 IN (0,1) THEN 'DIFY' ELSE 'FAST_API' END,
    capability_type='VISION_INFERENCE',
    workflow_code=CASE WHEN r.rn%12 IN (0,1) THEN 'SHOWCASE-IMAGE-ANALYSIS' ELSE NULL END,
    workflow_version=CASE WHEN r.rn%12 IN (0,1) THEN 'competition-v1' ELSE NULL END,
    fallback_used=(r.rn%12 IN (0,1)),
    fallback_provider_code=CASE WHEN r.rn%12 IN (0,1) THEN 'FAST_API' ELSE NULL END,
    fallback_reason=CASE WHEN r.rn%12 IN (0,1) THEN '演示：智能工作流暂不可用，已自动使用本地视觉能力完成分析' ELSE NULL END,
    updated_at=CURRENT_TIMESTAMP
FROM ranked r
WHERE t.id=r.id;

-- 居民反馈事件时间线：按最终状态构造一致的提交→受理→处理中→整改待复验过程。
WITH reports AS (
  SELECT rr.id AS report_id, rr.report_code, rr.status, rr.submitted_at,
         row_number() OVER (ORDER BY rr.report_code,rr.id) AS rn
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
), expanded AS (
  SELECT r.*, gs AS seq,
         pg_temp.showcase_uuid('resident-event:' || r.report_code || ':' || gs::text) AS event_id
  FROM reports r
  CROSS JOIN LATERAL generate_series(
    1,
    CASE
      WHEN r.status='ACCEPTED' THEN 2
      WHEN r.status='PROCESSING' THEN 3
      ELSE 4
    END
  ) gs
)
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT e.event_id, e.report_id,
       CASE e.seq
         WHEN 1 THEN 'SUBMITTED'
         WHEN 2 THEN 'ACCEPTED'
         WHEN 3 THEN 'STATUS_CHANGED'
         ELSE 'RECTIFICATION_SUBMITTED' END,
       CASE e.seq
         WHEN 1 THEN NULL
         WHEN 2 THEN 'SUBMITTED'
         WHEN 3 THEN 'ACCEPTED'
         ELSE 'PROCESSING' END,
       CASE e.seq
         WHEN 1 THEN 'SUBMITTED'
         WHEN 2 THEN 'ACCEPTED'
         WHEN 3 THEN 'PROCESSING'
         ELSE 'RESOLVED' END,
       CASE e.seq
         WHEN 1 THEN '居民提交建筑公共部位异常线索（比赛演示数据）。'
         WHEN 2 THEN '社区受理线索并关联现场巡检任务。'
         WHEN 3 THEN '现场巡检、AI 辅助识别和人工复核完成，进入整改处置。'
         ELSE '整改材料与整改后证据已提交，等待复查复验。' END,
       'PUBLIC',
       CASE e.seq WHEN 1 THEN 'CITIZEN' ELSE 'STAFF' END,
       CASE e.seq WHEN 1 THEN citizen.id ELSE staff.id END,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseClosure',true,
         'businessDataSynthetic',true,
         'sequence',e.seq,
         'requiresReinspection',(e.seq=4),
         'formalRiskChanged',false
       ),
       e.submitted_at + make_interval(hours => CASE e.seq WHEN 1 THEN 0 WHEN 2 THEN 4 WHEN 3 THEN 28 ELSE 52 END)
FROM expanded e
JOIN core.user_account citizen ON citizen.username='demo_inspector' AND citizen.deleted_at IS NULL
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_type=EXCLUDED.event_type, from_status=EXCLUDED.from_status,
  to_status=EXCLUDED.to_status, message=EXCLUDED.message,
  visibility=EXCLUDED.visibility, actor_type=EXCLUDED.actor_type,
  actor_user_id=EXCLUDED.actor_user_id, event_data=EXCLUDED.event_data,
  created_at=EXCLUDED.created_at;

-- 整改证据：为“处理中/待复验/已闭环”演示工单预置至少一张整改后图片。
-- PROCESSING 工单因此可在比赛现场直接演示“提交整改并进入待复验”，无需临时寻找图片文件。
WITH reports AS (
  SELECT rr.id AS report_id, rr.report_code,
         row_number() OVER (ORDER BY rr.report_code,rr.id) AS rn
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status IN ('PROCESSING','RESOLVED','CLOSED')
), assets AS (
  SELECT fa.id AS asset_id,
         row_number() OVER (ORDER BY fa.id) AS asset_rn,
         count(*) OVER () AS asset_count
  FROM asset.file_asset fa
  WHERE fa.deleted_at IS NULL
    AND fa.upload_status='AVAILABLE'
    AND COALESCE(fa.metadata->>'showcaseClosure','false')='true'
    AND COALESCE(fa.metadata->>'syntheticImage','false')='true'
), chosen AS (
  SELECT r.report_id, r.report_code, a.asset_id
  FROM reports r
  JOIN assets a ON a.asset_rn = 1 + ((r.rn - 1) % NULLIF(a.asset_count,0))
)
INSERT INTO asset.asset_binding (
  id, asset_id, business_type, business_id, binding_role
)
SELECT pg_temp.showcase_uuid('rectification-photo:' || c.report_code),
       c.asset_id, 'RESIDENT_REPORT', c.report_id, 'RECTIFICATION_PHOTO'
FROM chosen c
ON CONFLICT (id) DO UPDATE SET
  asset_id=EXCLUDED.asset_id,
  business_type=EXCLUDED.business_type,
  business_id=EXCLUDED.business_id,
  binding_role=EXCLUDED.binding_role,
  updated_at=CURRENT_TIMESTAMP,
  deleted_at=NULL;

-- 复查复验任务：RESOLVED 工单均拥有真实 REINSPECTION 任务，状态分布覆盖待开始/进行中/已完成；
-- CLOSED 工单保留已完成复查任务，形成可追溯的治理闭环。
WITH reports AS (
  SELECT rr.id AS report_id, rr.report_code, rr.building_id, rr.status, rr.submitted_at,
         row_number() OVER (ORDER BY rr.report_code,rr.id) AS rn
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status IN ('RESOLVED','CLOSED')
    AND rr.building_id IS NOT NULL
), prepared AS (
  SELECT r.*,
         pg_temp.showcase_uuid('reinspection-task:' || r.report_code) AS task_id,
         ('SHOWCASE-RI-' || substr(md5(r.report_code),1,16))::varchar(64) AS task_code,
         CASE
           WHEN r.status='CLOSED' THEN 'COMPLETED'
           WHEN r.rn%3=0 THEN 'COMPLETED'
           WHEN r.rn%3=1 THEN 'PENDING'
           ELSE 'IN_PROGRESS' END AS task_status
  FROM reports r
)
INSERT INTO core.inspection_task (
  id, task_code, building_id, inspection_type, planned_at, assigned_to, focus_parts,
  status, created_by, title, description, started_at, completed_at, remark
)
SELECT p.task_id, p.task_code, p.building_id, 'REINSPECTION',
       p.submitted_at + INTERVAL '3 days', inspector.id,
       to_jsonb(ARRAY['原问题位置','整改完整性','整改前后证据','是否仍存在原问题']),
       p.task_status, manager.id,
       '整改复查复验 · ' || p.report_code,
       '复查原问题位置，核对整改证据并记录是否通过复验。',
       CASE WHEN p.task_status IN ('IN_PROGRESS','COMPLETED') THEN p.submitted_at + INTERVAL '3 days 30 minutes' ELSE NULL END,
       CASE WHEN p.task_status='COMPLETED' THEN p.submitted_at + INTERVAL '3 days 2 hours' ELSE NULL END,
       '比赛演示复查复验任务；现场问题、整改和复验均为模拟业务数据'
FROM prepared p
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  task_code=EXCLUDED.task_code,
  building_id=EXCLUDED.building_id,
  inspection_type='REINSPECTION',
  planned_at=EXCLUDED.planned_at,
  assigned_to=EXCLUDED.assigned_to,
  focus_parts=EXCLUDED.focus_parts,
  status=EXCLUDED.status,
  created_by=EXCLUDED.created_by,
  title=EXCLUDED.title,
  description=EXCLUDED.description,
  started_at=EXCLUDED.started_at,
  completed_at=EXCLUDED.completed_at,
  remark=EXCLUDED.remark,
  updated_at=CURRENT_TIMESTAMP,
  deleted_at=NULL;

-- 将反馈工单与复查任务通过事件关联，供管理列表与闭环服务直接读取。
WITH reports AS (
  SELECT rr.id AS report_id, rr.report_code, rr.status, rr.submitted_at,
         pg_temp.showcase_uuid('reinspection-task:' || rr.report_code) AS task_id,
         ('SHOWCASE-RI-' || substr(md5(rr.report_code),1,16))::varchar(64) AS task_code
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status IN ('RESOLVED','CLOSED')
    AND rr.building_id IS NOT NULL
)
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('reinspection-created-event:' || r.report_code),
       r.report_id, 'REINSPECTION_CREATED', 'RESOLVED', 'RESOLVED',
       '整改已完成，已安排复查复验。',
       'PUBLIC', 'STAFF', manager.id,
       jsonb_build_object(
         'taskId',r.task_id,
         'taskCode',r.task_code,
         'inspectionType','REINSPECTION',
         'showcaseGenerated',true,
         'businessDataSynthetic',true,
         'formalRiskChanged',false
       ),
       r.submitted_at + INTERVAL '3 days'
FROM reports r
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_type=EXCLUDED.event_type,
  from_status=EXCLUDED.from_status,
  to_status=EXCLUDED.to_status,
  message=EXCLUDED.message,
  visibility=EXCLUDED.visibility,
  actor_type=EXCLUDED.actor_type,
  actor_user_id=EXCLUDED.actor_user_id,
  event_data=EXCLUDED.event_data,
  created_at=EXCLUDED.created_at;

-- 已关闭工单追加“复验通过”事件，避免 CLOSED 只是静态状态而没有治理依据。
WITH reports AS (
  SELECT rr.id AS report_id, rr.report_code, rr.submitted_at,
         pg_temp.showcase_uuid('reinspection-task:' || rr.report_code) AS task_id,
         ('SHOWCASE-RI-' || substr(md5(rr.report_code),1,16))::varchar(64) AS task_code
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status='CLOSED'
)
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('reinspection-passed-event:' || r.report_code),
       r.report_id, 'REINSPECTION_PASSED', 'RESOLVED', 'CLOSED',
       '复查复验通过，整改事项已闭环。',
       'PUBLIC', 'STAFF', manager.id,
       jsonb_build_object(
         'taskId',r.task_id,
         'taskCode',r.task_code,
         'passed',true,
         'summary','整改部位复查符合演示闭环要求，未发现原问题继续存在。',
         'showcaseGenerated',true,
         'businessDataSynthetic',true,
         'formalRiskChanged',false
       ),
       r.submitted_at + INTERVAL '3 days 3 hours'
FROM reports r
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_type=EXCLUDED.event_type,
  from_status=EXCLUDED.from_status,
  to_status=EXCLUDED.to_status,
  message=EXCLUDED.message,
  visibility=EXCLUDED.visibility,
  actor_type=EXCLUDED.actor_type,
  actor_user_id=EXCLUDED.actor_user_id,
  event_data=EXCLUDED.event_data,
  created_at=EXCLUDED.created_at;

COMMIT;
