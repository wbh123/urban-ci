-- 武汉 100 小区比赛数据最终硬闸门。
-- 任意一项逐楼栋闭环不满足即返回非零，禁止输出比赛可用 PASS。

DO $$
DECLARE community_count integer;
DECLARE located_community_count integer;
DECLARE building_count integer;
DECLARE district_count integer;
DECLARE bad_community_building_count integer;
DECLARE missing_inspection integer;
DECLARE missing_record integer;
DECLARE missing_photo integer;
DECLARE missing_evidence integer;
DECLARE missing_feedback integer;
DECLARE missing_feedback_timeline integer;
DECLARE missing_ai integer;
DECLARE missing_ai_success integer;
DECLARE missing_ai_structured integer;
DECLARE missing_review integer;
DECLARE missing_risk integer;
DECLARE missing_priority integer;
DECLARE missing_rectification_evidence integer;
DECLARE missing_reinspection_link integer;
DECLARE missing_closed_reinspection integer;
DECLARE recent_ai integer;
DECLARE detection_count integer;
DECLARE public_source_count integer;
DECLARE missing_status text;
DECLARE missing_level text;
DECLARE missing_priority_level text;
DECLARE missing_reinspection_status text;
BEGIN
  SELECT count(*) INTO community_count
  FROM core.community c
  WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%';
  IF community_count < 100 THEN
    RAISE EXCEPTION '比赛数据小区不足：要求 >=100，实际 %', community_count;
  END IF;

  SELECT count(*) INTO located_community_count
  FROM geo.community_location cl
  JOIN core.community c ON c.id=cl.community_id
  WHERE cl.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%';
  IF located_community_count <> community_count THEN
    RAISE EXCEPTION '小区定位覆盖不足：小区 %，已定位 %', community_count, located_community_count;
  END IF;

  SELECT count(DISTINCT district) INTO district_count
  FROM (
    SELECT CASE
      WHEN c.administrative_region LIKE '%江岸区%' THEN '江岸区'
      WHEN c.administrative_region LIKE '%江汉区%' THEN '江汉区'
      WHEN c.administrative_region LIKE '%硚口区%' THEN '硚口区'
      WHEN c.administrative_region LIKE '%汉阳区%' THEN '汉阳区'
      WHEN c.administrative_region LIKE '%武昌区%' THEN '武昌区'
      WHEN c.administrative_region LIKE '%青山区%' THEN '青山区'
      WHEN c.administrative_region LIKE '%洪山区%' THEN '洪山区'
      ELSE NULL END AS district
    FROM core.community c
    WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
  ) d
  WHERE district IS NOT NULL;
  IF district_count < 7 THEN
    RAISE EXCEPTION '武汉中心城区覆盖不足：要求 7 区，实际 % 区', district_count;
  END IF;

  SELECT count(*) INTO building_count
  FROM core.building b
  JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
  WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%';
  IF building_count < 800 THEN
    RAISE EXCEPTION '比赛楼栋不足：要求 >=800，实际 %', building_count;
  END IF;

  SELECT count(*) INTO bad_community_building_count
  FROM core.community c
  WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
    AND (SELECT count(*) FROM core.building b WHERE b.community_id=c.id AND b.deleted_at IS NULL) < 8;
  IF bad_community_building_count > 0 THEN
    RAISE EXCEPTION '有 % 个小区楼栋数不足 8', bad_community_building_count;
  END IF;

  WITH buildings AS (
    SELECT b.id, b.building_code
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
    WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
  )
  SELECT
    count(*) FILTER (WHERE inspection_count < 3),
    count(*) FILTER (WHERE record_count < 3),
    count(*) FILTER (WHERE photo_count < 3),
    count(*) FILTER (WHERE evidence_count < 2),
    count(*) FILTER (WHERE feedback_count < 1),
    count(*) FILTER (WHERE feedback_event_count < 3),
    count(*) FILTER (WHERE ai_count < 3),
    count(*) FILTER (WHERE ai_success_count < 2),
    count(*) FILTER (WHERE ai_structured_count < 2),
    count(*) FILTER (WHERE review_count < 1),
    count(*) FILTER (WHERE risk_count < 1),
    count(*) FILTER (WHERE priority_count < 1)
  INTO missing_inspection, missing_record, missing_photo, missing_evidence,
       missing_feedback, missing_feedback_timeline, missing_ai, missing_ai_success,
       missing_ai_structured, missing_review, missing_risk, missing_priority
  FROM (
    SELECT b.id,
      (SELECT count(*) FROM core.inspection_task t WHERE t.building_id=b.id AND t.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%') AS inspection_count,
      (SELECT count(*) FROM core.inspection_record r JOIN core.inspection_task t ON t.id=r.inspection_task_id WHERE r.building_id=b.id AND r.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%') AS record_count,
      (SELECT count(*) FROM asset.asset_binding ab JOIN core.inspection_record r ON r.id=ab.business_id JOIN core.inspection_task t ON t.id=r.inspection_task_id WHERE r.building_id=b.id AND ab.deleted_at IS NULL AND r.deleted_at IS NULL AND t.task_code LIKE 'SHOWCASE-CLOSE-%' AND ab.business_type='INSPECTION_RECORD' AND ab.binding_role='PHOTO') AS photo_count,
      (SELECT count(*) FROM core.building_evidence e WHERE e.building_id=b.id AND e.deleted_at IS NULL AND COALESCE(e.evidence_data->>'showcaseClosure','false')='true') AS evidence_count,
      (SELECT count(*) FROM core.resident_report rr WHERE rr.building_id=b.id AND rr.deleted_at IS NULL AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%') AS feedback_count,
      (SELECT count(*) FROM core.resident_report_event ev JOIN core.resident_report rr ON rr.id=ev.resident_report_id WHERE rr.building_id=b.id AND rr.deleted_at IS NULL AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%' AND COALESCE(ev.event_data->>'showcaseClosure','false')='true') AS feedback_event_count,
      (SELECT count(*) FROM ai.inference_task ait WHERE ait.building_id=b.id AND ait.request_code LIKE 'SHOWCASE-AI-%') AS ai_count,
      (SELECT count(*) FROM ai.inference_task ait WHERE ait.building_id=b.id AND ait.request_code LIKE 'SHOWCASE-AI-%' AND ait.status='SUCCEEDED') AS ai_success_count,
      (SELECT count(*) FROM ai.inference_result ir JOIN ai.inference_task ait ON ait.id=ir.inference_task_id WHERE ait.building_id=b.id AND ait.request_code LIKE 'SHOWCASE-AI-%' AND ait.status='SUCCEEDED' AND COALESCE(ir.structured_result->>'showcaseGenerated','false')='true') AS ai_structured_count,
      (SELECT count(*) FROM ai.inference_review ar JOIN ai.inference_task ait ON ait.id=ar.inference_task_id WHERE ait.building_id=b.id AND ait.request_code LIKE 'SHOWCASE-AI-%') AS review_count,
      (SELECT count(*) FROM core.risk_assessment ra WHERE ra.building_id=b.id AND ra.status IN ('CURRENT','STALE')) AS risk_count,
      (SELECT count(*) FROM core.renewal_priority rp WHERE rp.building_id=b.id AND rp.ranking_scope_key='ALL' AND rp.status IN ('CURRENT','STALE')) AS priority_count
    FROM buildings b
  ) coverage;

  IF missing_inspection + missing_record + missing_photo + missing_evidence + missing_feedback +
     missing_feedback_timeline + missing_ai + missing_ai_success + missing_ai_structured +
     missing_review + missing_risk + missing_priority > 0 THEN
    RAISE EXCEPTION '逐楼栋闭环失败：巡检=% 记录=% 图片=% 证据=% 反馈=% 反馈时间线=% AI=% AI成功=% AI结构化=% 复核=% 风险=% 优先级=%',
      missing_inspection, missing_record, missing_photo, missing_evidence, missing_feedback,
      missing_feedback_timeline, missing_ai, missing_ai_success, missing_ai_structured,
      missing_review, missing_risk, missing_priority;
  END IF;

  -- 整改证据硬闸门：所有已进入整改阶段的展示工单必须有 RECTIFICATION_PHOTO。
  SELECT count(*) INTO missing_rectification_evidence
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status IN ('PROCESSING','RESOLVED','CLOSED')
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
  IF missing_rectification_evidence > 0 THEN
    RAISE EXCEPTION '整改证据覆盖不足：有 % 条展示工单缺少 RECTIFICATION_PHOTO', missing_rectification_evidence;
  END IF;

  -- 待复验/已闭环工单必须关联真实 REINSPECTION 任务。
  SELECT count(*) INTO missing_reinspection_link
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status IN ('RESOLVED','CLOSED')
    AND NOT EXISTS (
      SELECT 1
      FROM core.resident_report_event ev
      JOIN core.inspection_task t
        ON t.id=CASE
          WHEN COALESCE(ev.event_data->>'taskId','') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          THEN (ev.event_data->>'taskId')::uuid
          ELSE NULL
        END
      WHERE ev.resident_report_id=rr.id
        AND ev.event_type='REINSPECTION_CREATED'
        AND t.inspection_type='REINSPECTION'
        AND t.deleted_at IS NULL
    );
  IF missing_reinspection_link > 0 THEN
    RAISE EXCEPTION '复查复验关联不足：有 % 条待复验/已闭环工单没有真实 REINSPECTION 任务', missing_reinspection_link;
  END IF;

  -- CLOSED 必须来自已完成复查和通过事件，不能只是静态状态。
  SELECT count(*) INTO missing_closed_reinspection
  FROM core.resident_report rr
  JOIN core.community c ON c.id=rr.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND rr.deleted_at IS NULL
    AND rr.report_code LIKE 'SHOWCASE-CLOSE-REPORT-%'
    AND rr.status='CLOSED'
    AND NOT EXISTS (
      SELECT 1
      FROM core.resident_report_event ev
      JOIN core.inspection_task t
        ON t.id=CASE
          WHEN COALESCE(ev.event_data->>'taskId','') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
          THEN (ev.event_data->>'taskId')::uuid
          ELSE NULL
        END
      WHERE ev.resident_report_id=rr.id
        AND ev.event_type='REINSPECTION_PASSED'
        AND COALESCE((ev.event_data->>'formalRiskChanged')::boolean,false)=false
        AND t.inspection_type='REINSPECTION'
        AND t.status='COMPLETED'
        AND t.deleted_at IS NULL
    );
  IF missing_closed_reinspection > 0 THEN
    RAISE EXCEPTION '闭环依据不足：有 % 条 CLOSED 展示工单缺少已完成复查或 REINSPECTION_PASSED 事件', missing_closed_reinspection;
  END IF;

  -- 待复验任务必须在比赛数据中同时具备待开始、进行中、已完成样例。
  SELECT string_agg(status, ', ' ORDER BY ord) INTO missing_reinspection_status
  FROM (VALUES (1,'PENDING'),(2,'IN_PROGRESS'),(3,'COMPLETED')) expected(ord,status)
  WHERE NOT EXISTS (
    SELECT 1
    FROM core.inspection_task t
    JOIN core.building b ON b.id=t.building_id
    JOIN core.community c ON c.id=b.community_id
    WHERE c.community_code LIKE 'SHOWCASE-WH-%'
      AND t.deleted_at IS NULL
      AND t.inspection_type='REINSPECTION'
      AND t.status=expected.status
  );
  IF missing_reinspection_status IS NOT NULL THEN
    RAISE EXCEPTION '复查复验任务状态未全覆盖：%', missing_reinspection_status;
  END IF;

  SELECT count(*) INTO recent_ai
  FROM ai.inference_task ait
  JOIN core.community c ON c.id=ait.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%'
    AND ait.request_code LIKE 'SHOWCASE-AI-%'
    AND ait.requested_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours';
  IF recent_ai < 100 THEN
    RAISE EXCEPTION '最近24小时 AI 动态不足：要求 >=100，实际 %', recent_ai;
  END IF;

  SELECT count(*) INTO detection_count
  FROM ai.detection d
  JOIN ai.inference_result ir ON ir.id=d.inference_result_id
  JOIN ai.inference_task ait ON ait.id=ir.inference_task_id
  JOIN core.community c ON c.id=ait.community_id
  WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND ait.request_code LIKE 'SHOWCASE-AI-%';
  IF detection_count < building_count * 2 THEN
    RAISE EXCEPTION 'AI detection 数量不足：楼栋 %，detection %，要求至少每栋2个', building_count, detection_count;
  END IF;

  SELECT string_agg(status, ', ' ORDER BY ord) INTO missing_status
  FROM (VALUES (1,'PENDING'),(2,'RUNNING'),(3,'SUCCEEDED'),(4,'FAILED')) expected(ord,status)
  WHERE NOT EXISTS (
    SELECT 1 FROM ai.inference_task ait
    JOIN core.community c ON c.id=ait.community_id
    WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND ait.request_code LIKE 'SHOWCASE-AI-%' AND ait.status=expected.status
  );
  IF missing_status IS NOT NULL THEN RAISE EXCEPTION 'AI 状态未全覆盖：%', missing_status; END IF;

  SELECT string_agg(level, ', ' ORDER BY ord) INTO missing_level
  FROM (VALUES (1,'LOW'),(2,'MEDIUM'),(3,'HIGH'),(4,'VERY_HIGH')) expected(ord,level)
  WHERE NOT EXISTS (
    SELECT 1 FROM core.risk_assessment r
    JOIN core.building b ON b.id=r.building_id
    JOIN core.community c ON c.id=b.community_id
    WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND r.status IN ('CURRENT','STALE') AND r.risk_level=expected.level
  );
  IF missing_level IS NOT NULL THEN RAISE EXCEPTION '风险等级未全覆盖：%', missing_level; END IF;

  SELECT string_agg(level, ', ' ORDER BY ord) INTO missing_priority_level
  FROM (VALUES (1,'P1'),(2,'P2'),(3,'P3'),(4,'P4')) expected(ord,level)
  WHERE NOT EXISTS (
    SELECT 1 FROM core.renewal_priority p
    JOIN core.building b ON b.id=p.building_id
    JOIN core.community c ON c.id=b.community_id
    WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND p.status IN ('CURRENT','STALE') AND p.ranking_scope_key='ALL' AND p.priority_level=expected.level
  );
  IF missing_priority_level IS NOT NULL THEN RAISE EXCEPTION '更新优先级未全覆盖：%', missing_priority_level; END IF;

  SELECT count(*) INTO public_source_count
  FROM core.community c
  WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
    AND COALESCE(c.extra_attributes->>'publicSourceVerified','false')='true';

  RAISE NOTICE '武汉比赛数据硬闸门通过：community=% building=% recentAI=% detections=% publicSourceMatched=%',
    community_count, building_count, recent_ai, detection_count, public_source_count;
  IF public_source_count=0 THEN
    RAISE NOTICE '未命中公开目录名称：不影响空间真实性，但建议后续通过缓存增量补充官方名单命中样本。';
  END IF;
END $$;

SELECT
  (SELECT count(*) FROM core.community c WHERE c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS communities,
  (SELECT count(*) FROM core.building b JOIN core.community c ON c.id=b.community_id WHERE b.deleted_at IS NULL AND c.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS buildings,
  (SELECT count(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE t.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS inspection_tasks,
  (SELECT count(*) FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id JOIN core.community c ON c.id=b.community_id WHERE t.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%' AND t.inspection_type='REINSPECTION') AS reinspection_tasks,
  (SELECT count(*) FROM core.inspection_record r JOIN core.building b ON b.id=r.building_id JOIN core.community c ON c.id=b.community_id WHERE r.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS inspection_records,
  (SELECT count(*) FROM ai.inference_task ait JOIN core.community c ON c.id=ait.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%') AS ai_tasks,
  (SELECT count(*) FROM ai.inference_result ir JOIN ai.inference_task ait ON ait.id=ir.inference_task_id JOIN core.community c ON c.id=ait.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND COALESCE(ir.structured_result->>'showcaseGenerated','false')='true') AS ai_structured_results,
  (SELECT count(*) FROM ai.detection d JOIN ai.inference_result ir ON ir.id=d.inference_result_id JOIN ai.inference_task ait ON ait.id=ir.inference_task_id JOIN core.community c ON c.id=ait.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%') AS ai_detections,
  (SELECT count(*) FROM ai.inference_review ar JOIN ai.inference_task ait ON ait.id=ar.inference_task_id JOIN core.community c ON c.id=ait.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%') AS ai_reviews,
  (SELECT count(*) FROM core.resident_report rr JOIN core.community c ON c.id=rr.community_id WHERE rr.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS feedback,
  (SELECT count(*) FROM core.resident_report_event ev JOIN core.resident_report rr ON rr.id=ev.resident_report_id JOIN core.community c ON c.id=rr.community_id WHERE c.community_code LIKE 'SHOWCASE-WH-%') AS feedback_events,
  (SELECT count(*) FROM asset.asset_binding ab JOIN core.resident_report rr ON rr.id=ab.business_id JOIN core.community c ON c.id=rr.community_id WHERE ab.business_type='RESIDENT_REPORT' AND ab.binding_role='RECTIFICATION_PHOTO' AND ab.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS rectification_photos,
  (SELECT count(*) FROM core.building_evidence e JOIN core.building b ON b.id=e.building_id JOIN core.community c ON c.id=b.community_id WHERE e.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%') AS evidence;
