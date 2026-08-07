-- 第四阶段评分演示数据。
-- 依赖：Flyway 已执行至 V14，且 seed-demo-data.sql 已创建 DEMO 楼栋和账号。
-- 仅删除并重建 engine_version=phase4-demo-seed-v1 的结果，可重复执行。

BEGIN;

DO $$
BEGIN
    IF to_regclass('core.completeness_assessment') IS NULL
       OR to_regclass('core.risk_assessment') IS NULL
       OR to_regclass('core.renewal_priority') IS NULL THEN
        RAISE EXCEPTION '第四阶段评分表不存在，请先执行 Flyway V14';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema='core' AND table_name='risk_assessment' AND column_name='risk_score'
    ) THEN
        RAISE EXCEPTION 'risk_score 字段不存在，请确认 Flyway V14 已执行';
    END IF;
END $$;

DELETE FROM core.renewal_priority WHERE engine_version='phase4-demo-seed-v1';
DELETE FROM core.risk_assessment WHERE engine_version='phase4-demo-seed-v1';
DELETE FROM core.completeness_assessment WHERE engine_version='phase4-demo-seed-v1';

CREATE TEMP TABLE tmp_phase4_demo_scores ON COMMIT DROP AS
SELECT
    b.id building_id, b.building_code, b.building_name, b.resident_count,
    c.id community_id, c.community_name, c.administrative_region,
    d.scenario_code, d.completeness_score, d.completeness_level,
    d.risk_score, d.confidence_score, d.reliability_score, d.risk_level,
    d.priority_score, d.priority_level, d.ranking,
    d.batch_id, d.completeness_id, d.risk_id, d.priority_id
FROM (
    VALUES
      ('DEMO-COMMUNITY-001','A-01','MISSING_PROFESSIONAL_AND_STABLE_TIE',
       68.00::numeric,'LIMITED',22.00::numeric,72.00::numeric,81.33::numeric,'LOW',
       36.00::numeric,'P4',5,
       '80000000-0000-4000-8000-000000000001'::uuid,
       '81000000-0000-4000-8000-000000000001'::uuid,
       '82000000-0000-4000-8000-000000000001'::uuid,
       '83000000-0000-4000-8000-000000000001'::uuid),
      ('DEMO-COMMUNITY-001','A-02','LOW_RISK_COMPLETE_AND_STABLE_TIE',
       94.00::numeric,'EXCELLENT',22.00::numeric,72.00::numeric,81.33::numeric,'LOW',
       36.00::numeric,'P4',4,
       '80000000-0000-4000-8000-000000000002'::uuid,
       '81000000-0000-4000-8000-000000000002'::uuid,
       '82000000-0000-4000-8000-000000000002'::uuid,
       '83000000-0000-4000-8000-000000000002'::uuid),
      ('DEMO-COMMUNITY-001','A-03','HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY',
       45.00::numeric,'INSUFFICIENT',72.00::numeric,48.00::numeric,55.00::numeric,'HIGH',
       68.20::numeric,'P2',2,
       '80000000-0000-4000-8000-000000000003'::uuid,
       '81000000-0000-4000-8000-000000000003'::uuid,
       '82000000-0000-4000-8000-000000000003'::uuid,
       '83000000-0000-4000-8000-000000000003'::uuid),
      ('DEMO-COMMUNITY-002','B-01','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI',
       90.00::numeric,'EXCELLENT',81.50::numeric,88.00::numeric,83.33::numeric,'VERY_HIGH',
       82.40::numeric,'P1',1,
       '80000000-0000-4000-8000-000000000004'::uuid,
       '81000000-0000-4000-8000-000000000004'::uuid,
       '82000000-0000-4000-8000-000000000004'::uuid,
       '83000000-0000-4000-8000-000000000004'::uuid),
      ('DEMO-COMMUNITY-002','B-02','MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK',
       72.00::numeric,'GOOD',48.00::numeric,70.00::numeric,65.33::numeric,'MEDIUM',
       52.60::numeric,'P3',3,
       '80000000-0000-4000-8000-000000000005'::uuid,
       '81000000-0000-4000-8000-000000000005'::uuid,
       '82000000-0000-4000-8000-000000000005'::uuid,
       '83000000-0000-4000-8000-000000000005'::uuid)
) d(community_code,building_code,scenario_code,completeness_score,completeness_level,
    risk_score,confidence_score,reliability_score,risk_level,priority_score,priority_level,
    ranking,batch_id,completeness_id,risk_id,priority_id)
JOIN core.community c ON c.community_code=d.community_code AND c.deleted_at IS NULL
JOIN core.building b ON b.community_id=c.id AND b.building_code=d.building_code AND b.deleted_at IS NULL;

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM tmp_phase4_demo_scores;
    IF n <> 5 THEN RAISE EXCEPTION '预期 5 栋演示楼栋，实际 % 栋', n; END IF;
END $$;

UPDATE core.renewal_priority p
SET status='SUPERSEDED', stale_reason='DEMO_SEED_REPLACED'
WHERE p.status='CURRENT' AND p.building_id IN (SELECT building_id FROM tmp_phase4_demo_scores);

UPDATE core.risk_assessment r
SET status='SUPERSEDED', stale_reason='DEMO_SEED_REPLACED', updated_at=CURRENT_TIMESTAMP
WHERE r.status='CURRENT' AND r.building_id IN (SELECT building_id FROM tmp_phase4_demo_scores);

UPDATE core.completeness_assessment a
SET status='SUPERSEDED', stale_reason='DEMO_SEED_REPLACED'
WHERE a.status='CURRENT' AND a.building_id IN (SELECT building_id FROM tmp_phase4_demo_scores);

INSERT INTO core.completeness_assessment (
    id,building_id,assessment_version,rule_version_id,completeness_score,completeness_level,
    available_items,missing_items,suggestions,dimension_scores,input_snapshot,input_checksum,
    status,assessed_at,created_at,calculation_batch_id,engine_version,trigger_type,triggered_by
)
SELECT
    d.completeness_id,d.building_id,'COMPLETENESS-V1',
    '41000000-0000-0000-0000-000000000001'::uuid,
    d.completeness_score,d.completeness_level,
    CASE
      WHEN d.scenario_code='LOW_RISK_COMPLETE_AND_STABLE_TIE'
        THEN '["基础档案","近期巡检","现场图片","维修资料","专业检测"]'::jsonb
      WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI'
        THEN '["基础档案","近期巡检","现场图片","维修资料","专业检测","经复核真实人工智能证据"]'::jsonb
      WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY'
        THEN '["基础档案","近期巡检","公众反馈","模拟人工智能结果（仅解释）"]'::jsonb
      ELSE '["基础档案","近期巡检","现场图片"]'::jsonb END,
    CASE
      WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI')
        THEN '[]'::jsonb
      WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY'
        THEN '["专业检测报告","完整维修档案","多部位现场图片"]'::jsonb
      ELSE '["专业检测报告"]'::jsonb END,
    CASE
      WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY'
        THEN '["优先安排现场复核","补充专业检测和多部位现场图片"]'::jsonb
      WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK'
        THEN '["核实重复公众反馈并补充专业检测"]'::jsonb
      WHEN d.scenario_code='MISSING_PROFESSIONAL_AND_STABLE_TIE'
        THEN '["补充第三方专业检测资料"]'::jsonb
      ELSE '[]'::jsonb END,
    jsonb_build_array(
      jsonb_build_object('code','BASIC_ARCHIVE','label','基础档案','score',
        CASE WHEN d.completeness_score>=85 THEN 100 ELSE 80 END,
        'weight',0.35,'contribution',CASE WHEN d.completeness_score>=85 THEN 35 ELSE 28 END,'evidenceCount',1),
      jsonb_build_object('code','RECENT_INSPECTION','label','近期巡检','score',
        CASE WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 70 ELSE 100 END,
        'weight',0.25,'contribution',CASE WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 17.5 ELSE 25 END,'evidenceCount',1),
      jsonb_build_object('code','IMAGE_COVERAGE','label','图片覆盖','score',
        CASE WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 0 ELSE 70 END,
        'weight',0.15,'contribution',CASE WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 0 ELSE 10.5 END,'evidenceCount',2),
      jsonb_build_object('code','MAINTENANCE_RECORD','label','维修资料','score',
        CASE WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI') THEN 100 ELSE 40 END,
        'weight',0.10,'contribution',CASE WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI') THEN 10 ELSE 4 END,'evidenceCount',1),
      jsonb_build_object('code','PROFESSIONAL_INSPECTION','label','专业检测','score',
        CASE WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI') THEN 100 ELSE 0 END,
        'weight',0.15,'contribution',CASE WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI') THEN 15 ELSE 0 END,
        'evidenceCount',CASE WHEN d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI') THEN 1 ELSE 0 END)
    ),
    s.input_snapshot,
    encode(digest(convert_to(s.input_snapshot::text,'UTF8'),'sha256'),'hex'),
    'CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,d.batch_id,'phase4-demo-seed-v1','DEMO_SEED',u.id
FROM tmp_phase4_demo_scores d
JOIN core.user_account u ON u.username='demo_admin' AND u.deleted_at IS NULL
CROSS JOIN LATERAL (
    SELECT jsonb_build_object(
      'schemaVersion','1.0','demoScenario',d.scenario_code,
      'building',jsonb_build_object(
        'buildingId',d.building_id,'buildingCode',d.building_code,
        'buildingName',d.building_name,'communityId',d.community_id,
        'communityName',d.community_name,'residentCount',d.resident_count),
      'evidenceSummary',jsonb_build_object(
        'mockAiOnly',d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY',
        'reviewedRealAi',d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI',
        'duplicateFeedbackSignals',CASE WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN 4 ELSE 0 END,
        'feedbackSignalsCounted',CASE WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN 3 ELSE 0 END,
        'professionalInspectionPresent',d.scenario_code IN ('LOW_RISK_COMPLETE_AND_STABLE_TIE','HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI')),
      'calculationContext',jsonb_build_object('triggerType','DEMO_SEED','engineVersion','phase4-demo-seed-v1')
    ) input_snapshot
) s;

UPDATE core.building b
SET archive_completeness_score=d.completeness_score,updated_at=CURRENT_TIMESTAMP
FROM tmp_phase4_demo_scores d
WHERE b.id=d.building_id;

INSERT INTO core.risk_assessment (
    id,assessment_code,building_id,assessment_version,rule_version_id,completeness_assessment_id,
    risk_score,confidence_score,evidence_reliability_score,risk_level,dimension_scores,
    score_explanation,input_snapshot,input_checksum,recommendation,need_manual_review,
    need_professional_inspection,status,assessed_at,created_at,updated_at,calculation_batch_id,
    engine_version,trigger_type,triggered_by
)
SELECT
    d.risk_id,'DEMO-RISK-'||replace(d.building_code,'-',''),d.building_id,'RISK-V1',
    '41000000-0000-0000-0000-000000000002'::uuid,d.completeness_id,
    d.risk_score,d.confidence_score,d.reliability_score,d.risk_level,
    jsonb_build_array(
      jsonb_build_object('code','BUILDING_BASE','label','楼龄与结构基础','score',
        CASE WHEN d.risk_score>=75 THEN 90 WHEN d.risk_score>=50 THEN 75 WHEN d.risk_score>=25 THEN 55 ELSE 30 END,
        'weight',0.20,'contribution',CASE WHEN d.risk_score>=75 THEN 18 WHEN d.risk_score>=50 THEN 15 WHEN d.risk_score>=25 THEN 11 ELSE 6 END,'evidenceCount',1),
      jsonb_build_object('code','INSPECTION_DEFECT','label','人工巡检病害','score',
        CASE WHEN d.risk_score>=75 THEN 95 WHEN d.risk_score>=50 THEN 80 WHEN d.risk_score>=25 THEN 50 ELSE 20 END,
        'weight',0.30,'contribution',CASE WHEN d.risk_score>=75 THEN 28.5 WHEN d.risk_score>=50 THEN 24 WHEN d.risk_score>=25 THEN 15 ELSE 6 END,'evidenceCount',1),
      jsonb_build_object('code','PROFESSIONAL_HISTORY','label','专业和历史证据','score',
        CASE WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN 90 WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 40 ELSE 30 END,
        'weight',0.20,'contribution',CASE WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN 18 WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 8 ELSE 6 END,'evidenceCount',1),
      jsonb_build_object('code','SPATIAL_ENVIRONMENT','label','空间与环境风险','score',
        CASE WHEN d.scenario_code LIKE 'HIGH_RISK%' THEN 60 ELSE 20 END,
        'weight',0.10,'contribution',CASE WHEN d.scenario_code LIKE 'HIGH_RISK%' THEN 6 ELSE 2 END,'evidenceCount',1),
      jsonb_build_object('code','RESIDENT_FEEDBACK','label','公众反馈','score',
        CASE WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN 60 WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 40 ELSE 10 END,
        'weight',0.10,'contribution',CASE WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN 6 WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN 4 ELSE 1 END,
        'evidenceCount',CASE WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN 3 ELSE 1 END),
      jsonb_build_object('code','REVIEWED_AI','label','经复核人工智能证据','score',
        CASE WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN 80 ELSE 0 END,
        'weight',0.10,'contribution',CASE WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN 8 ELSE 0 END,
        'evidenceCount',CASE WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN 1 ELSE 0 END)
    ),
    jsonb_build_object(
      'topFactors',CASE
        WHEN d.scenario_code='HIGH_RISK_HIGH_COMPLETENESS_REVIEWED_REAL_AI' THEN jsonb_build_array(
          jsonb_build_object('factorCode','SEVERE_INSPECTION','label','近期人工巡检发现严重病害','effect',28.50,'direction','INCREASE','sourceType','INSPECTION_RECORD','sourceId',d.building_id),
          jsonb_build_object('factorCode','REVIEWED_REAL_AI','label','经专业复核的真实人工智能病害证据','effect',8.00,'direction','INCREASE','sourceType','AI_REVIEW','sourceId','demo-real-ai-confirmed'))
        WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN jsonb_build_array(
          jsonb_build_object('factorCode','SEVERE_INSPECTION','label','严重巡检病害且缺少专业检测','effect',24.00,'direction','INCREASE','sourceType','INSPECTION_RECORD','sourceId',d.building_id),
          jsonb_build_object('factorCode','LOW_CONFIDENCE','label','资料完整度较低，当前判断需复核','effect',0,'direction','REVIEW','sourceType','COMPLETENESS','sourceId',d.completeness_id))
        WHEN d.scenario_code='MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK' THEN jsonb_build_array(
          jsonb_build_object('factorCode','PUBLIC_FEEDBACK_CAP','label','同类公众反馈按30天最多3条计入','effect',6.00,'direction','INCREASE','sourceType','RESIDENT_REPORT','sourceId','demo-feedback-cap'))
        ELSE jsonb_build_array(
          jsonb_build_object('factorCode','ROUTINE_TRACKING','label','当前风险特征较低，维持常规跟踪','effect',d.risk_score,'direction','INCREASE','sourceType','BUILDING','sourceId',d.building_id)) END,
      'excludedEvidence',CASE WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY'
        THEN jsonb_build_array(jsonb_build_object('reason','MOCK_AI_NOT_ELIGIBLE','sourceId','demo-mock-ai-only','contribution',0))
        ELSE '[]'::jsonb END,
      'missingData',CASE
        WHEN d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY' THEN '["专业检测报告","完整维修资料"]'::jsonb
        WHEN d.scenario_code IN ('MISSING_PROFESSIONAL_AND_STABLE_TIE','MEDIUM_RISK_DUPLICATE_PUBLIC_FEEDBACK') THEN '["专业检测报告"]'::jsonb
        ELSE '[]'::jsonb END,
      'recommendations',CASE
        WHEN d.risk_score>=75 THEN '["立即安排人工复核","建议开展第三方专业检测"]'::jsonb
        WHEN d.risk_score>=50 AND d.confidence_score<60 THEN '["安排人工复核","补充资料并开展专业检测"]'::jsonb
        WHEN d.risk_score>=50 THEN '["安排人工复核"]'::jsonb
        WHEN d.confidence_score<60 THEN '["补充资料或开展现场复核"]'::jsonb
        ELSE '["维持常规巡检和资料更新"]'::jsonb END,
      'confidenceLevel',CASE WHEN d.confidence_score>=80 THEN 'HIGH' WHEN d.confidence_score>=60 THEN 'MEDIUM' ELSE 'LOW' END,
      'disclaimer','系统结果仅用于风险筛查与辅助决策，不作为正式房屋安全鉴定结论。对于高风险、低置信度或资料完整度不足的结果，应安排人工复核或第三方专业检测。'
    ),
    a.input_snapshot,a.input_checksum,
    CASE
      WHEN d.risk_score>=75 THEN '立即安排人工复核，并建议开展第三方专业检测。'
      WHEN d.risk_score>=50 AND d.confidence_score<60 THEN '安排人工复核，补充资料并开展专业检测。'
      WHEN d.risk_score>=50 THEN '建议安排人工复核。'
      WHEN d.confidence_score<60 THEN '建议补充资料或开展现场复核。'
      ELSE '维持常规巡检和资料更新。' END,
    d.risk_score>=50 OR d.confidence_score<60,
    d.risk_score>=75 OR d.scenario_code='HIGH_RISK_LOW_COMPLETENESS_MOCK_ONLY',
    'CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
    d.batch_id,'phase4-demo-seed-v1','DEMO_SEED',u.id
FROM tmp_phase4_demo_scores d
JOIN core.completeness_assessment a ON a.id=d.completeness_id
JOIN core.user_account u ON u.username='demo_admin' AND u.deleted_at IS NULL;

WITH ordered_priorities AS (
    SELECT d.*,
           row_number() OVER (
               ORDER BY d.priority_score DESC,
                        d.risk_score DESC,
                        d.confidence_score DESC,
                        d.resident_count DESC,
                        d.building_code ASC,
                        d.building_id ASC
           )::integer AS calculated_ranking
    FROM tmp_phase4_demo_scores d
)
INSERT INTO core.renewal_priority (
    id,building_id,risk_assessment_id,rule_version_id,priority_version,priority_score,
    priority_level,ranking,ranking_scope,ranking_scope_key,factor_details,input_snapshot,
    input_checksum,recommendation,status,generated_at,created_at,calculation_batch_id,
    engine_version,trigger_type,triggered_by
)
SELECT
    d.priority_id,d.building_id,d.risk_id,
    '41000000-0000-0000-0000-000000000003'::uuid,'RENEWAL-V1',
    d.priority_score,d.priority_level,d.calculated_ranking,
    jsonb_build_object('scopeType','ALL','scopeId',NULL,'scopeKey','ALL',
      'orderedAt',CURRENT_TIMESTAMP,
      'sortKeys',jsonb_build_array('priorityScore DESC','riskScore DESC',
        'confidenceScore DESC','residentCount DESC','buildingCode ASC','buildingId ASC')),
    'ALL',
    jsonb_build_object(
      'factors',jsonb_build_array(
        jsonb_build_object('code','RISK','label','安全风险筛查','score',d.risk_score,'weight',0.45,'contribution',round(d.risk_score*0.45,2)),
        jsonb_build_object('code','POPULATION','label','人口影响','score',LEAST(100,20+d.resident_count/5.0),'weight',0.15,'contribution',round(LEAST(100,20+d.resident_count/5.0)*0.15,2)),
        jsonb_build_object('code','CONFIDENCE_FACTOR','label','资料可靠性调整','score',d.confidence_score,'weight',0.15,'contribution',round(0.85+0.15*d.confidence_score/100,4))),
      'reliabilityFactor',round(0.85+0.15*d.confidence_score/100,4),
      'recommendations',CASE
        WHEN d.priority_level='P1' THEN '["优先纳入治理或更新评估，并先完成专业检测"]'::jsonb
        WHEN d.priority_level='P2' THEN '["列入近期治理评估清单并完成人工复核"]'::jsonb
        WHEN d.priority_level='P3' THEN '["持续跟踪并核实重复公众反馈"]'::jsonb
        ELSE '["维持常规跟踪和资料更新"]'::jsonb END,
      'demoScenario',d.scenario_code,
      'disclaimer','城市更新优先级是辅助排序，不等同于行政立项决定或法定房屋安全结论。'),
    r.input_snapshot,r.input_checksum,
    CASE
      WHEN d.priority_level='P1' THEN '优先纳入治理或更新评估，并先完成专业检测。'
      WHEN d.priority_level='P2' THEN '列入近期治理评估清单并完成人工复核。'
      WHEN d.priority_level='P3' THEN '持续跟踪并核实重复公众反馈。'
      ELSE '维持常规跟踪和资料更新。' END,
    'CURRENT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,d.batch_id,
    'phase4-demo-seed-v1','DEMO_SEED',u.id
FROM tmp_phase4_demo_scores d
JOIN core.risk_assessment r ON r.id=d.risk_id
JOIN core.user_account u ON u.username='demo_admin' AND u.deleted_at IS NULL;

DO $$
DECLARE
    c_count integer; r_count integer; p_count integer;
    a02_rank integer; a01_rank integer;
BEGIN
    SELECT count(*) INTO c_count FROM core.completeness_assessment
    WHERE engine_version='phase4-demo-seed-v1' AND status='CURRENT';
    SELECT count(*) INTO r_count FROM core.risk_assessment
    WHERE engine_version='phase4-demo-seed-v1' AND status='CURRENT';
    SELECT count(*) INTO p_count FROM core.renewal_priority
    WHERE engine_version='phase4-demo-seed-v1' AND status='CURRENT' AND ranking_scope_key='ALL';
    IF c_count<>5 OR r_count<>5 OR p_count<>5 THEN
        RAISE EXCEPTION '演示结果数量异常：completeness=%, risk=%, priority=%',c_count,r_count,p_count;
    END IF;

    SELECT p.ranking INTO a02_rank FROM core.renewal_priority p
    JOIN core.building b ON b.id=p.building_id
    WHERE p.engine_version='phase4-demo-seed-v1' AND p.ranking_scope_key='ALL'
      AND p.status='CURRENT' AND b.building_code='A-02';
    SELECT p.ranking INTO a01_rank FROM core.renewal_priority p
    JOIN core.building b ON b.id=p.building_id
    WHERE p.engine_version='phase4-demo-seed-v1' AND p.ranking_scope_key='ALL'
      AND p.status='CURRENT' AND b.building_code='A-01';
    IF a02_rank>=a01_rank THEN
        RAISE EXCEPTION '稳定同分排序异常：A-02 rank=%，A-01 rank=%',a02_rank,a01_rank;
    END IF;
END $$;

COMMIT;

-- B-01：高风险高完整度、经复核 REAL 证据。
-- A-03：高风险低完整度、只有 MOCK 结果、缺少专业检测。
-- B-02：中风险、同类公众反馈按 30 天最多 3 条计入。
-- A-02：低风险资料完整。
-- A-01：缺少专业检测。
-- A-01 与 A-02：稳定同分排序样例。
