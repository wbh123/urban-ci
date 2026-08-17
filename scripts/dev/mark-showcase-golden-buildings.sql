-- 比赛主演示黄金楼栋选择。
-- 仅作用于 SHOWCASE-WH-* 展示数据，不修改真实业务域。
-- 黄金楼栋要求业务链完整；REAL 视觉结果由 preflight 单独强校验，禁止将 MOCK 冒充 REAL。

BEGIN;

UPDATE core.building b
SET extra_attributes = (COALESCE(b.extra_attributes, '{}'::jsonb)
        - 'showcaseGolden' - 'showcaseGoldenSlot' - 'showcaseGoldenPreparedAt'),
    updated_at = CURRENT_TIMESTAMP
FROM core.community c
WHERE c.id=b.community_id
  AND c.deleted_at IS NULL
  AND b.deleted_at IS NULL
  AND c.community_code LIKE 'SHOWCASE-WH-%'
  AND COALESCE(b.extra_attributes->>'showcaseGolden','false')='true';

CREATE TEMP TABLE tmp_showcase_golden ON COMMIT DROP AS
WITH candidates AS (
  SELECT b.id AS building_id,
         b.building_code,
         b.building_name,
         c.community_name,
         r.risk_score,
         r.risk_level,
         p.priority_score,
         p.priority_level,
         EXISTS (
           SELECT 1
           FROM ai.inference_task t
           WHERE t.building_id=b.id
             AND t.mode='REAL'
             AND t.status='SUCCEEDED'
         ) AS has_real_ai,
         COUNT(DISTINCT ir.id) AS completed_records,
         COUNT(DISTINCT fa.id) AS available_images
  FROM core.building b
  JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
  JOIN core.completeness_assessment co
    ON co.building_id=b.id AND co.status='CURRENT'
  JOIN core.risk_assessment r
    ON r.building_id=b.id AND r.status='CURRENT'
  JOIN core.renewal_priority p
    ON p.building_id=b.id AND p.status='CURRENT' AND p.ranking_scope_key='ALL'
  JOIN core.inspection_record ir
    ON ir.building_id=b.id AND ir.deleted_at IS NULL AND ir.status='COMPLETED'
  JOIN asset.asset_binding ab
    ON ab.business_type='INSPECTION_RECORD'
   AND ab.business_id=ir.id
   AND ab.deleted_at IS NULL
   AND ab.binding_role='PHOTO'
  JOIN asset.file_asset fa
    ON fa.id=ab.asset_id AND fa.deleted_at IS NULL AND fa.upload_status='AVAILABLE'
  WHERE b.deleted_at IS NULL
    AND c.community_code LIKE 'SHOWCASE-WH-%'
  GROUP BY b.id,b.building_code,b.building_name,c.community_name,
           r.risk_score,r.risk_level,p.priority_score,p.priority_level
), ranked AS (
  SELECT *, row_number() OVER (
      ORDER BY has_real_ai DESC,
               risk_score DESC NULLS LAST,
               priority_score DESC NULLS LAST,
               building_code,
               building_id
  ) AS slot
  FROM candidates
  WHERE completed_records > 0 AND available_images > 0
)
SELECT * FROM ranked WHERE slot <= 3;

DO $$
DECLARE n integer;
BEGIN
  SELECT count(*) INTO n FROM tmp_showcase_golden;
  IF n < 3 THEN
    RAISE EXCEPTION '黄金演示楼栋至少需要 3 栋完整样本，当前仅 % 栋；请先补齐 CURRENT 评分、巡检记录和图片证据', n;
  END IF;
END $$;

UPDATE core.building b
SET extra_attributes=COALESCE(b.extra_attributes,'{}'::jsonb) || jsonb_build_object(
      'showcaseGolden', true,
      'showcaseGoldenSlot', g.slot,
      'showcaseGoldenPreparedAt', CURRENT_TIMESTAMP,
      'showcaseGoldenHasRealAi', g.has_real_ai
    ),
    updated_at=CURRENT_TIMESTAMP
FROM tmp_showcase_golden g
WHERE b.id=g.building_id;

COMMIT;

SELECT
  b.extra_attributes->>'showcaseGoldenSlot' AS slot,
  c.community_name,
  b.building_code,
  b.building_name,
  r.risk_level,
  r.risk_score,
  p.priority_level,
  p.priority_score,
  b.extra_attributes->>'showcaseGoldenHasRealAi' AS has_real_ai
FROM core.building b
JOIN core.community c ON c.id=b.community_id
LEFT JOIN core.risk_assessment r ON r.building_id=b.id AND r.status='CURRENT'
LEFT JOIN core.renewal_priority p
  ON p.building_id=b.id AND p.status='CURRENT' AND p.ranking_scope_key='ALL'
WHERE COALESCE(b.extra_attributes->>'showcaseGolden','false')='true'
ORDER BY (b.extra_attributes->>'showcaseGoldenSlot')::integer;
