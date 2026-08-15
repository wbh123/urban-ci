-- 展示数据核心状态覆盖。
-- 仅作用于 SHOWCASE-WH-* 小区中的楼栋；正式业务数据不得执行本文件。
-- 前提：calculate-showcase-assessments.sh 已为至少 6 栋 showcase 楼栋生成 CURRENT 评分与 ALL 范围优先级。

BEGIN;

CREATE TEMP TABLE tmp_showcase_coverage_building ON COMMIT DROP AS
SELECT building_id, rn
FROM (
    SELECT b.id AS building_id,
           row_number() OVER (ORDER BY c.community_code, b.building_code, b.id) AS rn
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
    WHERE b.deleted_at IS NULL
      AND c.community_code LIKE 'SHOWCASE-WH-%'
      AND EXISTS (
          SELECT 1 FROM core.risk_assessment r
          WHERE r.building_id=b.id AND r.status='CURRENT'
      )
      AND EXISTS (
          SELECT 1 FROM core.completeness_assessment co
          WHERE co.building_id=b.id AND co.status='CURRENT'
      )
      AND EXISTS (
          SELECT 1 FROM core.renewal_priority p
          WHERE p.building_id=b.id
            AND p.ranking_scope_key='ALL'
            AND p.status='CURRENT'
      )
) ranked
WHERE rn <= 6;

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM tmp_showcase_coverage_building;
    IF n < 6 THEN
        RAISE EXCEPTION '展示状态覆盖至少需要 6 栋已完成评分的 SHOWCASE 楼栋，当前仅 % 栋', n;
    END IF;
END $$;

-- 1~4 号样本保证四档风险全覆盖。
UPDATE core.risk_assessment r
SET risk_score = CASE s.rn
        WHEN 1 THEN 88.00
        WHEN 2 THEN 72.00
        WHEN 3 THEN 48.00
        WHEN 4 THEN 22.00
        ELSE r.risk_score END,
    risk_level = CASE s.rn
        WHEN 1 THEN 'VERY_HIGH'
        WHEN 2 THEN 'HIGH'
        WHEN 3 THEN 'MEDIUM'
        WHEN 4 THEN 'LOW'
        ELSE r.risk_level END,
    confidence_score = CASE s.rn
        WHEN 1 THEN 91.00
        WHEN 2 THEN 82.00
        WHEN 3 THEN 74.00
        WHEN 4 THEN 88.00
        ELSE r.confidence_score END,
    need_manual_review = CASE WHEN s.rn IN (1, 2) THEN TRUE ELSE r.need_manual_review END,
    input_snapshot = COALESCE(r.input_snapshot, '{}'::jsonb)
        || jsonb_build_object(
            'showcaseCoverage', TRUE,
            'showcaseCoverageDimension', 'RISK_PRIORITY',
            'showcaseCoverageSlot', s.rn,
            'showcaseRiskLevel', CASE s.rn
                WHEN 1 THEN 'VERY_HIGH'
                WHEN 2 THEN 'HIGH'
                WHEN 3 THEN 'MEDIUM'
                WHEN 4 THEN 'LOW' END),
    recommendation = concat_ws(' ', NULLIF(r.recommendation, ''), '[展示状态覆盖样本]'),
    updated_at = CURRENT_TIMESTAMP
FROM tmp_showcase_coverage_building s
WHERE r.building_id=s.building_id
  AND s.rn BETWEEN 1 AND 4
  AND r.status='CURRENT';

-- 1~4 号样本同时保证 ALL 范围 P1~P4 全覆盖。
UPDATE core.renewal_priority p
SET priority_score = CASE s.rn
        WHEN 1 THEN 92.00
        WHEN 2 THEN 76.00
        WHEN 3 THEN 55.00
        WHEN 4 THEN 30.00
        ELSE p.priority_score END,
    priority_level = CASE s.rn
        WHEN 1 THEN 'P1'
        WHEN 2 THEN 'P2'
        WHEN 3 THEN 'P3'
        WHEN 4 THEN 'P4'
        ELSE p.priority_level END,
    input_snapshot = COALESCE(p.input_snapshot, '{}'::jsonb)
        || jsonb_build_object(
            'showcaseCoverage', TRUE,
            'showcaseCoverageDimension', 'RISK_PRIORITY',
            'showcaseCoverageSlot', s.rn,
            'showcasePriorityLevel', CASE s.rn
                WHEN 1 THEN 'P1'
                WHEN 2 THEN 'P2'
                WHEN 3 THEN 'P3'
                WHEN 4 THEN 'P4' END),
    recommendation = concat_ws(' ', NULLIF(p.recommendation, ''), '[展示状态覆盖样本]')
FROM tmp_showcase_coverage_building s
WHERE p.building_id=s.building_id
  AND s.rn BETWEEN 1 AND 4
  AND p.ranking_scope_key='ALL'
  AND p.status='CURRENT';

-- 第 5 栋生成 STALE 状态；风险/完整度/优先级一起过期，保持语义一致。
UPDATE core.risk_assessment r
SET status='STALE',
    input_snapshot=COALESCE(r.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"STALE"}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
FROM tmp_showcase_coverage_building s
WHERE r.building_id=s.building_id AND s.rn=5 AND r.status='CURRENT';

UPDATE core.completeness_assessment co
SET status='STALE',
    input_snapshot=COALESCE(co.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"STALE"}'::jsonb
FROM tmp_showcase_coverage_building s
WHERE co.building_id=s.building_id AND s.rn=5 AND co.status='CURRENT';

UPDATE core.renewal_priority p
SET status='STALE',
    input_snapshot=COALESCE(p.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"STALE"}'::jsonb
FROM tmp_showcase_coverage_building s
WHERE p.building_id=s.building_id AND s.rn=5 AND p.status='CURRENT';

-- 第 6 栋生成 NO_RESULT：评分历史保留但退出 CURRENT/STALE 查询范围。
UPDATE core.risk_assessment r
SET status='SUPERSEDED',
    input_snapshot=COALESCE(r.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"NO_RESULT"}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
FROM tmp_showcase_coverage_building s
WHERE r.building_id=s.building_id AND s.rn=6 AND r.status IN ('CURRENT','STALE');

UPDATE core.completeness_assessment co
SET status='SUPERSEDED',
    input_snapshot=COALESCE(co.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"NO_RESULT"}'::jsonb
FROM tmp_showcase_coverage_building s
WHERE co.building_id=s.building_id AND s.rn=6 AND co.status IN ('CURRENT','STALE');

UPDATE core.renewal_priority p
SET status='SUPERSEDED',
    input_snapshot=COALESCE(p.input_snapshot, '{}'::jsonb)
        || '{"showcaseCoverage":true,"showcaseFreshness":"NO_RESULT"}'::jsonb
FROM tmp_showcase_coverage_building s
WHERE p.building_id=s.building_id AND s.rn=6 AND p.status IN ('CURRENT','STALE');

-- 公众反馈覆盖完整状态机，保证筛选和统计页面每种状态均有样本。
CREATE TEMP TABLE tmp_showcase_feedback_coverage ON COMMIT DROP AS
SELECT report_id, rn
FROM (
    SELECT rr.id AS report_id,
           row_number() OVER (ORDER BY rr.report_code, rr.id) AS rn
    FROM core.resident_report rr
    JOIN core.community c ON c.id=rr.community_id AND c.deleted_at IS NULL
    WHERE rr.deleted_at IS NULL
      AND c.community_code LIKE 'SHOWCASE-WH-%'
      AND rr.report_code LIKE 'SHOWCASE-REPORT-%'
) ranked
WHERE rn <= 8;

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n FROM tmp_showcase_feedback_coverage;
    IF n < 8 THEN
        RAISE EXCEPTION '公众反馈状态覆盖至少需要 8 条 SHOWCASE 反馈，当前仅 % 条', n;
    END IF;
END $$;

UPDATE core.resident_report rr
SET status = CASE s.rn
        WHEN 1 THEN 'SUBMITTED'
        WHEN 2 THEN 'ACCEPTED'
        WHEN 3 THEN 'PROCESSING'
        WHEN 4 THEN 'NEED_MORE_INFO'
        WHEN 5 THEN 'RESOLVED'
        WHEN 6 THEN 'CLOSED'
        WHEN 7 THEN 'REJECTED'
        WHEN 8 THEN 'CANCELLED'
        ELSE rr.status END,
    urgency = CASE ((s.rn - 1) % 4)
        WHEN 0 THEN 'LOW'
        WHEN 1 THEN 'NORMAL'
        WHEN 2 THEN 'HIGH'
        ELSE 'URGENT' END,
    feedback_channel = CASE ((s.rn - 1) % 5)
        WHEN 0 THEN 'WEB'
        WHEN 1 THEN 'PHONE'
        WHEN 2 THEN 'SMS'
        WHEN 3 THEN 'COUNTER'
        ELSE 'INTERNAL' END,
    handling_summary = concat('展示状态覆盖样本：', CASE s.rn
        WHEN 1 THEN '已提交'
        WHEN 2 THEN '已受理'
        WHEN 3 THEN '处理中'
        WHEN 4 THEN '待补充信息'
        WHEN 5 THEN '已解决'
        WHEN 6 THEN '已关闭'
        WHEN 7 THEN '已驳回'
        WHEN 8 THEN '已取消' END),
    handled_at = CASE WHEN s.rn=1 THEN NULL ELSE CURRENT_TIMESTAMP - INTERVAL '2 hours' END,
    closed_at = CASE WHEN s.rn IN (6,7,8) THEN CURRENT_TIMESTAMP - INTERVAL '1 hour' ELSE NULL END,
    updated_by = (
        SELECT u.id FROM core.user_account u
        WHERE u.username='demo_community' AND u.deleted_at IS NULL
        LIMIT 1
    ),
    updated_at = CURRENT_TIMESTAMP
FROM tmp_showcase_feedback_coverage s
WHERE rr.id=s.report_id;

-- SQL 内部自检：大屏核心枚举与公众反馈完整状态机必须全部有样本。
DO $$
DECLARE missing text;
BEGIN
    SELECT string_agg(expected.status, ', ' ORDER BY expected.ordinal)
    INTO missing
    FROM (
      VALUES
        (1,'SUBMITTED'),(2,'ACCEPTED'),(3,'PROCESSING'),(4,'NEED_MORE_INFO'),
        (5,'RESOLVED'),(6,'CLOSED'),(7,'REJECTED'),(8,'CANCELLED')
    ) expected(ordinal,status)
    WHERE NOT EXISTS (
      SELECT 1
      FROM core.resident_report rr
      JOIN core.community c ON c.id=rr.community_id AND c.deleted_at IS NULL
      WHERE rr.deleted_at IS NULL
        AND c.community_code LIKE 'SHOWCASE-WH-%'
        AND rr.status=expected.status
    );
    IF missing IS NOT NULL THEN
      RAISE EXCEPTION '公众反馈状态未全覆盖：%', missing;
    END IF;
    RAISE NOTICE '公众反馈状态覆盖完成：SUBMITTED/ACCEPTED/PROCESSING/NEED_MORE_INFO/RESOLVED/CLOSED/REJECTED/CANCELLED';
END $$;

COMMIT;
