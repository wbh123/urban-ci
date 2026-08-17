-- 比赛 real 模式专项数据：复检系统建议 + 人工最终决策。
-- 与 SHOWCASE-CLOSE-* 主闭环数据隔离，仅创建 4 条 SHOWCASE-DECISION-* 专用工单。
-- 脚本可重复执行：会重置这 4 条工单的人工演示事件、整改证据和现场复检任务。

BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.showcase_uuid(value text) RETURNS uuid LANGUAGE SQL IMMUTABLE AS $$
  SELECT (substr(md5(value),1,8)||'-'||substr(md5(value),9,4)||'-'||substr(md5(value),13,4)||'-'||substr(md5(value),17,4)||'-'||substr(md5(value),21,12))::uuid
$$;

DROP TABLE IF EXISTS tmp_showcase_decision_buildings;
CREATE TEMP TABLE tmp_showcase_decision_buildings ON COMMIT DROP AS
SELECT b.id AS building_id,
       b.community_id,
       b.building_code,
       COALESCE(b.building_name,b.building_code) AS building_name,
       c.community_name,
       row_number() OVER (ORDER BY c.community_code,b.building_code,b.id) AS rn
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE b.deleted_at IS NULL
  AND c.community_code LIKE 'SHOWCASE-WH-%'
ORDER BY c.community_code,b.building_code,b.id
LIMIT 4;

DO $$
DECLARE selected_count integer;
DECLARE asset_count integer;
BEGIN
  SELECT count(*) INTO selected_count FROM tmp_showcase_decision_buildings;
  IF selected_count <> 4 THEN
    RAISE EXCEPTION '复检人工决策演示需要 4 栋展示楼栋，实际 %', selected_count;
  END IF;

  SELECT count(*) INTO asset_count
  FROM asset.file_asset fa
  WHERE fa.deleted_at IS NULL
    AND fa.upload_status='AVAILABLE'
    AND COALESCE(fa.metadata->>'showcaseClosure','false')='true'
    AND COALESCE(fa.metadata->>'syntheticImage','false')='true';
  IF asset_count < 1 THEN
    RAISE EXCEPTION '复检人工决策演示缺少可复用的整改证据图片';
  END IF;
END $$;

-- 1. 创建四类确定性工单：
--    低风险处理中：现场演示系统建议 WAIVED；
--    高风险待复验：现场演示人工覆盖 REQUIRED 建议；
--    历史免复检：展示系统建议与人工决定一致；
--    历史人工覆盖：展示工作人员覆盖系统 REQUIRED 建议并完整留痕。
WITH scenarios AS (
  SELECT * FROM (VALUES
    (1,
     'SHOWCASE-DECISION-LOW-PROCESSING',
     'WATER_LEAKAGE',
     '公共走廊墙面出现轻微潮湿痕迹，已完成局部防水修补和清洁处理，整改照片齐全。',
     'PROCESSING',
     'NORMAL',
     8),
    (2,
     'SHOWCASE-DECISION-HIGH-RESOLVED',
     'WALL_CRACK',
     '外墙饰面附近发现裂缝，整改材料已提交，等待工作人员确认是否需要继续现场复检。',
     'RESOLVED',
     'HIGH',
     7),
    (3,
     'SHOWCASE-DECISION-WAIVED-HISTORY',
     'WATER_LEAKAGE',
     '雨后窗边曾出现轻微潮湿，防水节点已修补，整改前后照片及维护记录完整。',
     'CLOSED',
     'NORMAL',
     6),
    (4,
     'SHOWCASE-DECISION-OVERRIDE-HISTORY',
     'WALL_CRACK',
     '住户曾反馈饰面层裂缝，整改后由专业人员复核原问题已消除并形成书面依据。',
     'CLOSED',
     'HIGH',
     5)
  ) AS v(scenario_no,report_code,report_type,description,status,urgency,hours_ago)
)
INSERT INTO core.resident_report (
  report_code, community_id, building_id, reporter_user_id, report_type,
  description, status, urgency, evidence, submitted_at, handled_at
)
SELECT sc.report_code::varchar(64),
       b.community_id,
       b.building_id,
       reporter.id,
       sc.report_type,
       sc.description,
       sc.status,
       sc.urgency,
       jsonb_build_array(jsonb_build_object(
         'type','SHOWCASE_TEXT',
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'note','复检人工决策专项演示数据，不对应真实居民或现实安全事件'
       )),
       CURRENT_TIMESTAMP - make_interval(hours => sc.hours_ago),
       CURRENT_TIMESTAMP - make_interval(hours => GREATEST(sc.hours_ago - 2,1))
FROM scenarios sc
JOIN tmp_showcase_decision_buildings b ON b.rn=sc.scenario_no
JOIN core.user_account reporter ON reporter.username='demo_inspector' AND reporter.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
  community_id=EXCLUDED.community_id,
  building_id=EXCLUDED.building_id,
  reporter_user_id=EXCLUDED.reporter_user_id,
  report_type=EXCLUDED.report_type,
  description=EXCLUDED.description,
  status=EXCLUDED.status,
  urgency=EXCLUDED.urgency,
  evidence=EXCLUDED.evidence,
  submitted_at=EXCLUDED.submitted_at,
  handled_at=EXCLUDED.handled_at,
  handling_summary=NULL,
  updated_at=CURRENT_TIMESTAMP;

-- 2. 若比赛现场曾经实际操作过这四条工单，重跑脚本时先清理专用工单产生的复检任务。
WITH decision_reports AS (
  SELECT rr.id
  FROM core.resident_report rr
  WHERE rr.deleted_at IS NULL
    AND rr.report_code IN (
      'SHOWCASE-DECISION-LOW-PROCESSING',
      'SHOWCASE-DECISION-HIGH-RESOLVED',
      'SHOWCASE-DECISION-WAIVED-HISTORY',
      'SHOWCASE-DECISION-OVERRIDE-HISTORY'
    )
), task_ids AS (
  SELECT DISTINCT (ev.event_data->>'taskId')::uuid AS task_id
  FROM core.resident_report_event ev
  JOIN decision_reports dr ON dr.id=ev.resident_report_id
  WHERE ev.event_type='REINSPECTION_CREATED'
    AND COALESCE(ev.event_data->>'taskId','') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
)
UPDATE core.inspection_task t
SET deleted_at=CURRENT_TIMESTAMP,
    updated_at=CURRENT_TIMESTAMP,
    remark=CASE
      WHEN COALESCE(t.remark,'')='' THEN '复检人工决策比赛演示重置'
      ELSE t.remark || '；复检人工决策比赛演示重置'
    END
WHERE t.id IN (SELECT task_id FROM task_ids)
  AND t.deleted_at IS NULL;

DELETE FROM core.resident_report_event ev
USING core.resident_report rr
WHERE ev.resident_report_id=rr.id
  AND rr.deleted_at IS NULL
  AND rr.report_code IN (
    'SHOWCASE-DECISION-LOW-PROCESSING',
    'SHOWCASE-DECISION-HIGH-RESOLVED',
    'SHOWCASE-DECISION-WAIVED-HISTORY',
    'SHOWCASE-DECISION-OVERRIDE-HISTORY'
  );

UPDATE asset.asset_binding ab
SET deleted_at=CURRENT_TIMESTAMP,
    updated_at=CURRENT_TIMESTAMP
FROM core.resident_report rr
WHERE ab.business_type='RESIDENT_REPORT'
  AND ab.business_id=rr.id
  AND ab.binding_role='RECTIFICATION_PHOTO'
  AND rr.deleted_at IS NULL
  AND rr.report_code IN (
    'SHOWCASE-DECISION-LOW-PROCESSING',
    'SHOWCASE-DECISION-HIGH-RESOLVED',
    'SHOWCASE-DECISION-WAIVED-HISTORY',
    'SHOWCASE-DECISION-OVERRIDE-HISTORY'
  )
  AND ab.deleted_at IS NULL;

-- 3. 四条工单均预置整改证据，确保 real 模式无需现场临时找图片。
WITH chosen_asset AS (
  SELECT fa.id AS asset_id
  FROM asset.file_asset fa
  WHERE fa.deleted_at IS NULL
    AND fa.upload_status='AVAILABLE'
    AND COALESCE(fa.metadata->>'showcaseClosure','false')='true'
    AND COALESCE(fa.metadata->>'syntheticImage','false')='true'
  ORDER BY fa.id
  LIMIT 1
), decision_reports AS (
  SELECT rr.id AS report_id, rr.report_code
  FROM core.resident_report rr
  WHERE rr.deleted_at IS NULL
    AND rr.report_code IN (
      'SHOWCASE-DECISION-LOW-PROCESSING',
      'SHOWCASE-DECISION-HIGH-RESOLVED',
      'SHOWCASE-DECISION-WAIVED-HISTORY',
      'SHOWCASE-DECISION-OVERRIDE-HISTORY'
    )
)
INSERT INTO asset.asset_binding (
  id, asset_id, business_type, business_id, binding_role
)
SELECT pg_temp.showcase_uuid('decision-rectification-photo:' || dr.report_code),
       ca.asset_id,
       'RESIDENT_REPORT',
       dr.report_id,
       'RECTIFICATION_PHOTO'
FROM decision_reports dr
CROSS JOIN chosen_asset ca
ON CONFLICT (id) DO UPDATE SET
  asset_id=EXCLUDED.asset_id,
  business_type=EXCLUDED.business_type,
  business_id=EXCLUDED.business_id,
  binding_role=EXCLUDED.binding_role,
  updated_at=CURRENT_TIMESTAMP,
  deleted_at=NULL;

-- 4. 基础公开时间线：提交 → 受理 → 处理中。
WITH decision_reports AS (
  SELECT rr.id AS report_id, rr.report_code, rr.submitted_at
  FROM core.resident_report rr
  WHERE rr.deleted_at IS NULL
    AND rr.report_code IN (
      'SHOWCASE-DECISION-LOW-PROCESSING',
      'SHOWCASE-DECISION-HIGH-RESOLVED',
      'SHOWCASE-DECISION-WAIVED-HISTORY',
      'SHOWCASE-DECISION-OVERRIDE-HISTORY'
    )
), expanded AS (
  SELECT dr.*, gs AS seq
  FROM decision_reports dr
  CROSS JOIN LATERAL generate_series(1,3) gs
)
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('decision-event:' || e.report_code || ':' || e.seq::text),
       e.report_id,
       CASE e.seq WHEN 1 THEN 'SUBMITTED' WHEN 2 THEN 'ACCEPTED' ELSE 'STATUS_CHANGED' END,
       CASE e.seq WHEN 1 THEN NULL WHEN 2 THEN 'SUBMITTED' ELSE 'ACCEPTED' END,
       CASE e.seq WHEN 1 THEN 'SUBMITTED' WHEN 2 THEN 'ACCEPTED' ELSE 'PROCESSING' END,
       CASE e.seq
         WHEN 1 THEN '居民提交建筑公共部位异常线索（比赛专项演示数据）。'
         WHEN 2 THEN '社区已受理并进入核查处置。'
         ELSE '工作人员结合现场材料完成处置，等待整改闭环决策。' END,
       'PUBLIC',
       CASE e.seq WHEN 1 THEN 'CITIZEN' ELSE 'STAFF' END,
       CASE e.seq WHEN 1 THEN citizen.id ELSE staff.id END,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'sequence',e.seq,
         'formalRiskChanged',false
       ),
       e.submitted_at + make_interval(hours => e.seq - 1)
FROM expanded e
JOIN core.user_account citizen ON citizen.username='demo_inspector' AND citizen.deleted_at IS NULL
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
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

-- 5. 高风险待复验：系统建议 REQUIRED，但暂未派现场任务，比赛时可人工选择“免复检”并填写覆盖理由。
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('decision-high-resolved'),
       rr.id,
       'RECTIFICATION_SUBMITTED',
       'PROCESSING',
       'RESOLVED',
       '整改材料与整改后证据已提交，等待人工确认是否需要现场复检。',
       'PUBLIC',
       'STAFF',
       staff.id,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'reinspectionDecision','REQUIRED',
         'recommendedDecision','REQUIRED',
         'recommendationReasons',jsonb_build_array(
           '紧急程度为 HIGH，建议通过现场复检确认整改效果',
           '问题类型 WALL_CRACK 涉及结构、坠落、违规改造或消防等现场核实事项',
           '问题描述命中需现场核实的风险信号：裂缝'
         ),
         'recommendationSource','STRUCTURED_RULES',
         'decisionSource','HUMAN_CONFIRMED',
         'manualOverride',false,
         'rectificationEvidenceCount',1,
         'requiresReinspection',true,
         'formalRiskChanged',false
       ),
       rr.submitted_at + INTERVAL '3 hours'
FROM core.resident_report rr
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
WHERE rr.report_code='SHOWCASE-DECISION-HIGH-RESOLVED'
  AND rr.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_data=EXCLUDED.event_data,
  message=EXCLUDED.message,
  created_at=EXCLUDED.created_at;

-- 6. 历史低风险免复检：系统建议 WAIVED，人工确认后直接闭环，未修改正式风险评分。
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('decision-waived-history'),
       rr.id,
       'RECTIFICATION_CLOSED_WITHOUT_REINSPECTION',
       'PROCESSING',
       'CLOSED',
       '整改已完成，经人工确认本次无需现场复检，事项已闭环。',
       'PUBLIC',
       'STAFF',
       staff.id,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'reinspectionDecision','WAIVED',
         'recommendedDecision','WAIVED',
         'recommendationReasons',jsonb_build_array(
           '当前结构化信息未发现高紧急程度、重点问题类型或明显现场风险信号，可由人工结合整改证据判断是否免复检'
         ),
         'recommendationSource','STRUCTURED_RULES',
         'decisionSource','HUMAN_CONFIRMED',
         'manualOverride',false,
         'decisionReason','整改前后照片完整，问题属于轻微防水维护，工作人员复核材料后一致同意无需再次现场复检。',
         'rectificationEvidenceCount',1,
         'requiresReinspection',false,
         'formalRiskChanged',false
       ),
       rr.submitted_at + INTERVAL '3 hours'
FROM core.resident_report rr
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
WHERE rr.report_code='SHOWCASE-DECISION-WAIVED-HISTORY'
  AND rr.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_data=EXCLUDED.event_data,
  message=EXCLUDED.message,
  created_at=EXCLUDED.created_at;

-- 7. 历史人工覆盖：结构化规则建议 REQUIRED，但专业人员结合整改证据人工确认无需继续复检并留痕。
INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('decision-override-required'),
       rr.id,
       'RECTIFICATION_SUBMITTED',
       'PROCESSING',
       'RESOLVED',
       '整改材料已提交，结构化规则建议安排现场复检。',
       'PUBLIC',
       'STAFF',
       staff.id,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'reinspectionDecision','REQUIRED',
         'recommendedDecision','REQUIRED',
         'recommendationReasons',jsonb_build_array(
           '紧急程度为 HIGH，建议通过现场复检确认整改效果',
           '问题类型 WALL_CRACK 涉及结构、坠落、违规改造或消防等现场核实事项',
           '问题描述命中需现场核实的风险信号：裂缝'
         ),
         'recommendationSource','STRUCTURED_RULES',
         'decisionSource','HUMAN_CONFIRMED',
         'manualOverride',false,
         'rectificationEvidenceCount',1,
         'requiresReinspection',true,
         'formalRiskChanged',false
       ),
       rr.submitted_at + INTERVAL '3 hours'
FROM core.resident_report rr
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
WHERE rr.report_code='SHOWCASE-DECISION-OVERRIDE-HISTORY'
  AND rr.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_data=EXCLUDED.event_data,
  message=EXCLUDED.message,
  created_at=EXCLUDED.created_at;

INSERT INTO core.resident_report_event (
  id, resident_report_id, event_type, from_status, to_status, message,
  visibility, actor_type, actor_user_id, event_data, created_at
)
SELECT pg_temp.showcase_uuid('decision-override-waived'),
       rr.id,
       'REINSPECTION_WAIVED',
       'RESOLVED',
       'CLOSED',
       '经人工复核，确认本次整改无需继续现场复检，事项已闭环。',
       'PUBLIC',
       'STAFF',
       staff.id,
       jsonb_build_object(
         'showcaseGenerated',true,
         'showcaseDecisionDemo',true,
         'businessDataSynthetic',true,
         'reinspectionDecision','WAIVED',
         'recommendedDecision','REQUIRED',
         'recommendationReasons',jsonb_build_array(
           '紧急程度为 HIGH，建议通过现场复检确认整改效果',
           '问题类型 WALL_CRACK 涉及结构、坠落、违规改造或消防等现场核实事项',
           '问题描述命中需现场核实的风险信号：裂缝'
         ),
         'recommendationSource','STRUCTURED_RULES',
         'decisionSource','HUMAN_CONFIRMED_AFTER_RESOLVED',
         'manualOverride',true,
         'decisionReason','专业人员复核整改前后照片、维修记录和现场核查记录后确认原异常已消除，负责人决定无需再次安排独立复检。',
         'requiresReinspection',false,
         'formalRiskChanged',false
       ),
       rr.submitted_at + INTERVAL '4 hours'
FROM core.resident_report rr
JOIN core.user_account staff ON staff.username='demo_community' AND staff.deleted_at IS NULL
WHERE rr.report_code='SHOWCASE-DECISION-OVERRIDE-HISTORY'
  AND rr.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  event_data=EXCLUDED.event_data,
  message=EXCLUDED.message,
  created_at=EXCLUDED.created_at;

COMMIT;
