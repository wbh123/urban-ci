-- 比赛 real 模式专项硬闸门：复检建议 + 人工最终决策必须具备可操作样例和历史审计样例。

DO $$
DECLARE missing_reports integer;
DECLARE missing_evidence integer;
DECLARE bad_low_processing integer;
DECLARE bad_high_resolved integer;
DECLARE bad_waived_history integer;
DECLARE bad_override_history integer;
DECLARE active_task_count integer;
BEGIN
  SELECT count(*) INTO missing_reports
  FROM (VALUES
    ('SHOWCASE-DECISION-LOW-PROCESSING'),
    ('SHOWCASE-DECISION-HIGH-RESOLVED'),
    ('SHOWCASE-DECISION-WAIVED-HISTORY'),
    ('SHOWCASE-DECISION-OVERRIDE-HISTORY')
  ) expected(report_code)
  WHERE NOT EXISTS (
    SELECT 1
    FROM core.resident_report rr
    JOIN core.community c ON c.id=rr.community_id
    WHERE rr.deleted_at IS NULL
      AND c.community_code LIKE 'SHOWCASE-WH-%'
      AND rr.report_code=expected.report_code
  );
  IF missing_reports > 0 THEN
    RAISE EXCEPTION '复检人工决策演示工单缺失：% 条', missing_reports;
  END IF;

  SELECT count(*) INTO missing_evidence
  FROM core.resident_report rr
  WHERE rr.deleted_at IS NULL
    AND rr.report_code IN (
      'SHOWCASE-DECISION-LOW-PROCESSING',
      'SHOWCASE-DECISION-HIGH-RESOLVED',
      'SHOWCASE-DECISION-WAIVED-HISTORY',
      'SHOWCASE-DECISION-OVERRIDE-HISTORY'
    )
    AND NOT EXISTS (
      SELECT 1
      FROM asset.asset_binding ab
      JOIN asset.file_asset fa ON fa.id=ab.asset_id
      WHERE ab.business_type='RESIDENT_REPORT'
        AND ab.business_id=rr.id
        AND ab.binding_role='RECTIFICATION_PHOTO'
        AND ab.deleted_at IS NULL
        AND fa.deleted_at IS NULL
        AND fa.upload_status='AVAILABLE'
    );
  IF missing_evidence > 0 THEN
    RAISE EXCEPTION '复检人工决策演示缺少整改证据：% 条', missing_evidence;
  END IF;

  SELECT count(*) INTO bad_low_processing
  FROM core.resident_report rr
  WHERE rr.report_code='SHOWCASE-DECISION-LOW-PROCESSING'
    AND rr.deleted_at IS NULL
    AND NOT (
      rr.status='PROCESSING'
      AND rr.report_type='WATER_LEAKAGE'
      AND rr.urgency='NORMAL'
      AND rr.description NOT LIKE '%裂缝%'
      AND rr.description NOT LIKE '%脱落%'
      AND rr.description NOT LIKE '%变形%'
      AND rr.description NOT LIKE '%消防%'
    );
  IF bad_low_processing > 0 THEN
    RAISE EXCEPTION '低风险处理中演示工单不满足系统建议 WAIVED 的结构化前提';
  END IF;

  SELECT count(*) INTO bad_high_resolved
  FROM core.resident_report rr
  WHERE rr.report_code='SHOWCASE-DECISION-HIGH-RESOLVED'
    AND rr.deleted_at IS NULL
    AND NOT (
      rr.status='RESOLVED'
      AND rr.report_type='WALL_CRACK'
      AND rr.urgency='HIGH'
      AND EXISTS (
        SELECT 1
        FROM core.resident_report_event ev
        WHERE ev.resident_report_id=rr.id
          AND ev.event_type='RECTIFICATION_SUBMITTED'
          AND ev.event_data->>'reinspectionDecision'='REQUIRED'
          AND ev.event_data->>'recommendedDecision'='REQUIRED'
          AND lower(COALESCE(ev.event_data->>'manualOverride','false'))='false'
          AND lower(COALESCE(ev.event_data->>'formalRiskChanged','true'))='false'
      )
      AND NOT EXISTS (
        SELECT 1
        FROM core.resident_report_event ev
        WHERE ev.resident_report_id=rr.id
          AND ev.event_type='REINSPECTION_CREATED'
      )
    );
  IF bad_high_resolved > 0 THEN
    RAISE EXCEPTION '高风险待复验演示工单必须保持 REQUIRED 建议且尚未派出复检任务';
  END IF;

  SELECT count(*) INTO bad_waived_history
  FROM core.resident_report rr
  WHERE rr.report_code='SHOWCASE-DECISION-WAIVED-HISTORY'
    AND rr.deleted_at IS NULL
    AND NOT (
      rr.status='CLOSED'
      AND EXISTS (
        SELECT 1
        FROM core.resident_report_event ev
        WHERE ev.resident_report_id=rr.id
          AND ev.event_type='RECTIFICATION_CLOSED_WITHOUT_REINSPECTION'
          AND ev.event_data->>'reinspectionDecision'='WAIVED'
          AND ev.event_data->>'recommendedDecision'='WAIVED'
          AND lower(COALESCE(ev.event_data->>'manualOverride','true'))='false'
          AND length(COALESCE(ev.event_data->>'decisionReason','')) >= 4
          AND lower(COALESCE(ev.event_data->>'formalRiskChanged','true'))='false'
      )
    );
  IF bad_waived_history > 0 THEN
    RAISE EXCEPTION '历史免复检样例缺少 WAIVED 一致决策或审计理由';
  END IF;

  SELECT count(*) INTO bad_override_history
  FROM core.resident_report rr
  WHERE rr.report_code='SHOWCASE-DECISION-OVERRIDE-HISTORY'
    AND rr.deleted_at IS NULL
    AND NOT (
      rr.status='CLOSED'
      AND EXISTS (
        SELECT 1
        FROM core.resident_report_event ev
        WHERE ev.resident_report_id=rr.id
          AND ev.event_type='REINSPECTION_WAIVED'
          AND ev.event_data->>'reinspectionDecision'='WAIVED'
          AND ev.event_data->>'recommendedDecision'='REQUIRED'
          AND lower(COALESCE(ev.event_data->>'manualOverride','false'))='true'
          AND length(COALESCE(ev.event_data->>'decisionReason','')) >= 4
          AND lower(COALESCE(ev.event_data->>'formalRiskChanged','true'))='false'
      )
    );
  IF bad_override_history > 0 THEN
    RAISE EXCEPTION '历史人工覆盖样例缺少 REQUIRED→WAIVED 覆盖留痕';
  END IF;

  -- 两条现场可操作样例在脚本重置后都不能残留有效复检任务。
  SELECT count(*) INTO active_task_count
  FROM core.resident_report rr
  JOIN core.resident_report_event ev ON ev.resident_report_id=rr.id
  JOIN core.inspection_task t
    ON t.id=CASE
      WHEN COALESCE(ev.event_data->>'taskId','') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      THEN (ev.event_data->>'taskId')::uuid
      ELSE NULL
    END
  WHERE rr.deleted_at IS NULL
    AND rr.report_code IN (
      'SHOWCASE-DECISION-LOW-PROCESSING',
      'SHOWCASE-DECISION-HIGH-RESOLVED'
    )
    AND ev.event_type='REINSPECTION_CREATED'
    AND t.deleted_at IS NULL
    AND t.status IN ('PENDING','IN_PROGRESS','COMPLETED');
  IF active_task_count > 0 THEN
    RAISE EXCEPTION '现场复检决策演示工单残留 % 个有效复检任务，无法稳定重复演示', active_task_count;
  END IF;

  RAISE NOTICE '复检人工决策 real-mode 演示数据通过：低风险处理中 + 高风险待复验 + 历史免复检 + 历史人工覆盖';
END $$;
