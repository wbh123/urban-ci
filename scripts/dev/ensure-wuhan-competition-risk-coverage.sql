-- 武汉 100 小区比赛数据风险分布整理。
-- 前提：calculate-showcase-assessments.sh 已为全部 SHOWCASE-WH-* 楼栋生成 CURRENT 评分。
-- 本文件不制造 NO_RESULT；每栋楼始终保留 CURRENT 或 STALE 的可展示正式评分结果。

BEGIN;

CREATE TEMP TABLE tmp_competition_ranked ON COMMIT DROP AS
SELECT b.id AS building_id,
       row_number() OVER (ORDER BY c.community_code,b.building_code,b.id) AS rn
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE b.deleted_at IS NULL
  AND c.community_code LIKE 'SHOWCASE-WH-%'
  AND EXISTS (SELECT 1 FROM core.risk_assessment r WHERE r.building_id=b.id AND r.status='CURRENT')
  AND EXISTS (SELECT 1 FROM core.completeness_assessment co WHERE co.building_id=b.id AND co.status='CURRENT')
  AND EXISTS (SELECT 1 FROM core.renewal_priority p WHERE p.building_id=b.id AND p.ranking_scope_key='ALL' AND p.status='CURRENT');

DO $$
DECLARE expected_count integer;
DECLARE ranked_count integer;
BEGIN
  SELECT count(*) INTO expected_count
  FROM core.building b
  JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
  WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%';
  SELECT count(*) INTO ranked_count FROM tmp_competition_ranked;
  IF ranked_count <> expected_count THEN
    RAISE EXCEPTION '风险分布整理前覆盖不完整：楼栋总数 %, 已评分 %', expected_count, ranked_count;
  END IF;
END $$;

-- 风险目标分布：LOW 36%、MEDIUM 34%、HIGH 21%、VERY_HIGH 9%。
-- 这是比赛固定种子的展示分布，仍保留原始 input_snapshot，并明确标记 showcaseSyntheticDistribution。
UPDATE core.risk_assessment r
SET risk_score = CASE
      WHEN ((s.rn-1)%100) < 9  THEN 86 + ((s.rn*7)%11)
      WHEN ((s.rn-1)%100) < 30 THEN 68 + ((s.rn*5)%15)
      WHEN ((s.rn-1)%100) < 64 THEN 42 + ((s.rn*3)%23)
      ELSE 18 + ((s.rn*7)%21) END,
    risk_level = CASE
      WHEN ((s.rn-1)%100) < 9  THEN 'VERY_HIGH'
      WHEN ((s.rn-1)%100) < 30 THEN 'HIGH'
      WHEN ((s.rn-1)%100) < 64 THEN 'MEDIUM'
      ELSE 'LOW' END,
    confidence_score = 72 + ((s.rn*11)%24),
    need_manual_review = (((s.rn-1)%100) < 30),
    input_snapshot = COALESCE(r.input_snapshot,'{}'::jsonb) || jsonb_build_object(
      'showcaseGenerated',true,
      'showcaseSyntheticDistribution',true,
      'showcaseDistributionVersion','WUHAN-COMPETITION-V1',
      'showcaseDisclaimer','风险档位分布为比赛演示数据，不代表对应真实武汉小区现实风险'
    ),
    recommendation = CASE
      WHEN ((s.rn-1)%100) < 9 THEN '比赛演示：建议列入最高关注对象，优先开展专业复核、治理处置和高频复巡。'
      WHEN ((s.rn-1)%100) < 30 THEN '比赛演示：建议近期开展专项复核并结合维修记录持续跟踪。'
      WHEN ((s.rn-1)%100) < 64 THEN '比赛演示：建议按计划维护并关注近期新增病害。'
      ELSE '比赛演示：保持常规巡检和档案更新。' END,
    updated_at=CURRENT_TIMESTAMP
FROM tmp_competition_ranked s
WHERE r.building_id=s.building_id AND r.status='CURRENT';

-- 更新优先级目标分布：P1 12%、P2 24%、P3 34%、P4 30%。
UPDATE core.renewal_priority p
SET priority_score = CASE
      WHEN ((s.rn-1)%100) < 12 THEN 88 + ((s.rn*5)%10)
      WHEN ((s.rn-1)%100) < 36 THEN 70 + ((s.rn*7)%16)
      WHEN ((s.rn-1)%100) < 70 THEN 45 + ((s.rn*3)%22)
      ELSE 20 + ((s.rn*5)%22) END,
    priority_level = CASE
      WHEN ((s.rn-1)%100) < 12 THEN 'P1'
      WHEN ((s.rn-1)%100) < 36 THEN 'P2'
      WHEN ((s.rn-1)%100) < 70 THEN 'P3'
      ELSE 'P4' END,
    input_snapshot = COALESCE(p.input_snapshot,'{}'::jsonb) || jsonb_build_object(
      'showcaseGenerated',true,
      'showcaseSyntheticDistribution',true,
      'showcaseDistributionVersion','WUHAN-COMPETITION-V1'
    ),
    recommendation = CASE
      WHEN ((s.rn-1)%100) < 12 THEN '比赛演示：优先纳入近期治理计划。'
      WHEN ((s.rn-1)%100) < 36 THEN '比赛演示：纳入重点储备并安排专项复核。'
      WHEN ((s.rn-1)%100) < 70 THEN '比赛演示：结合维护周期滚动更新。'
      ELSE '比赛演示：维持常规治理节奏。' END
FROM tmp_competition_ranked s
WHERE p.building_id=s.building_id AND p.ranking_scope_key='ALL' AND p.status='CURRENT';

-- 约 4% 结果设置为 STALE，用于展示“需要重新研判”，但仍然存在可读取结果，不产生空详情。
UPDATE core.risk_assessment r
SET status='STALE', stale_reason='SHOWCASE_REFRESH_DUE',
    input_snapshot=COALESCE(r.input_snapshot,'{}'::jsonb) || '{"showcaseFreshness":"STALE"}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
FROM tmp_competition_ranked s
WHERE r.building_id=s.building_id AND r.status='CURRENT' AND s.rn%25=0;

UPDATE core.completeness_assessment co
SET status='STALE', stale_reason='SHOWCASE_REFRESH_DUE',
    input_snapshot=COALESCE(co.input_snapshot,'{}'::jsonb) || '{"showcaseFreshness":"STALE"}'::jsonb
FROM tmp_competition_ranked s
WHERE co.building_id=s.building_id AND co.status='CURRENT' AND s.rn%25=0;

UPDATE core.renewal_priority p
SET status='STALE', stale_reason='SHOWCASE_REFRESH_DUE',
    input_snapshot=COALESCE(p.input_snapshot,'{}'::jsonb) || '{"showcaseFreshness":"STALE"}'::jsonb
FROM tmp_competition_ranked s
WHERE p.building_id=s.building_id AND p.ranking_scope_key='ALL' AND p.status='CURRENT' AND s.rn%25=0;

DO $$
DECLARE missing text;
DECLARE no_result integer;
BEGIN
  SELECT string_agg(level, ', ' ORDER BY ord) INTO missing
  FROM (VALUES (1,'LOW'),(2,'MEDIUM'),(3,'HIGH'),(4,'VERY_HIGH')) AS expected(ord,level)
  WHERE NOT EXISTS (
    SELECT 1 FROM core.risk_assessment r
    JOIN core.building b ON b.id=r.building_id
    JOIN core.community c ON c.id=b.community_id
    WHERE c.community_code LIKE 'SHOWCASE-WH-%' AND r.status IN ('CURRENT','STALE') AND r.risk_level=expected.level
  );
  IF missing IS NOT NULL THEN RAISE EXCEPTION '比赛风险等级未全覆盖：%', missing; END IF;

  SELECT count(*) INTO no_result
  FROM core.building b
  JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
  WHERE b.deleted_at IS NULL AND c.community_code LIKE 'SHOWCASE-WH-%'
    AND NOT EXISTS (SELECT 1 FROM core.risk_assessment r WHERE r.building_id=b.id AND r.status IN ('CURRENT','STALE'));
  IF no_result > 0 THEN RAISE EXCEPTION '比赛数据禁止 NO_RESULT：仍有 % 栋楼没有可展示风险结果', no_result; END IF;
END $$;

COMMIT;
