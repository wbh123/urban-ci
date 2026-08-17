\set ON_ERROR_STOP on

-- REAL 视觉检测一致性诊断/修复。
-- 两条真实视觉持久化路径的模型原始检测集合来源不同：
--   1) 编排路径：ai.inference_result.structured_result.detections
--   2) ACCURACY / AiInferenceRepository 路径：ai.inference_result.raw_output_snapshot.detections
-- 本脚本统一选择可用的模型原始检测集合，再与 ai.detection 持久化投影逐条比较。
-- 默认 dry-run；只有显式 --apply 才补齐缺失 sequence_no。绝不自动删除“数据库多于原始快照”的明细。
-- 若补齐的是已 CONFIRMED/CORRECTED 的 REAL 结果，则仅把已有正式评分标记 STALE，绝不自动改分。

CREATE TEMP TABLE tmp_ai_detection_inventory AS
SELECT
    t.id AS inference_id,
    t.asset_id,
    t.building_id,
    t.review_status,
    r.id AS result_id,
    CASE
        WHEN jsonb_typeof(r.structured_result->'detections')='array' THEN 'STRUCTURED_RESULT'
        WHEN jsonb_typeof(r.raw_output_snapshot->'detections')='array' THEN 'RAW_OUTPUT_SNAPSHOT'
        ELSE 'NONE'
    END AS canonical_source,
    CASE
        WHEN jsonb_typeof(r.structured_result->'detections')='array'
            THEN r.structured_result->'detections'
        WHEN jsonb_typeof(r.raw_output_snapshot->'detections')='array'
            THEN r.raw_output_snapshot->'detections'
        ELSE '[]'::jsonb
    END AS canonical_detections
FROM ai.inference_task t
JOIN ai.inference_result r ON r.inference_task_id=t.id
WHERE t.mode='REAL'
  AND t.status='SUCCEEDED';

CREATE TEMP TABLE tmp_ai_detection_repair_candidates AS
WITH source AS (
    SELECT
        i.inference_id,
        i.asset_id,
        i.building_id,
        i.review_status,
        i.result_id,
        i.canonical_source,
        det.ordinality::integer AS sequence_no,
        det.value AS detection,
        CASE WHEN jsonb_typeof(det.value->'boundingBox'->'x')='number'
             THEN (det.value->'boundingBox'->>'x')::double precision END AS raw_x,
        CASE WHEN jsonb_typeof(det.value->'boundingBox'->'y')='number'
             THEN (det.value->'boundingBox'->>'y')::double precision END AS raw_y,
        CASE WHEN jsonb_typeof(det.value->'boundingBox'->'width')='number'
             THEN (det.value->'boundingBox'->>'width')::double precision END AS raw_width,
        CASE WHEN jsonb_typeof(det.value->'boundingBox'->'height')='number'
             THEN (det.value->'boundingBox'->>'height')::double precision END AS raw_height
    FROM tmp_ai_detection_inventory i
    CROSS JOIN LATERAL jsonb_array_elements(i.canonical_detections)
        WITH ORDINALITY AS det(value, ordinality)
), validated AS (
    SELECT *,
        raw_x IS NOT NULL AND raw_y IS NOT NULL
        AND raw_width IS NOT NULL AND raw_height IS NOT NULL
        AND raw_x BETWEEN -0.000001 AND 1.000001
        AND raw_y BETWEEN -0.000001 AND 1.000001
        AND raw_width > 0 AND raw_height > 0
        AND raw_width <= 1.000001 AND raw_height <= 1.000001
        AND raw_x + raw_width <= 1.000001
        AND raw_y + raw_height <= 1.000001 AS repairable
    FROM source
)
SELECT
    inference_id,
    asset_id,
    building_id,
    review_status,
    result_id,
    canonical_source,
    sequence_no,
    detection,
    repairable,
    GREATEST(0.0, LEAST(1.0, raw_x)) AS bbox_x,
    GREATEST(0.0, LEAST(1.0, raw_y)) AS bbox_y,
    LEAST(raw_width, 1.0 - GREATEST(0.0, LEAST(1.0, raw_x))) AS bbox_width,
    LEAST(raw_height, 1.0 - GREATEST(0.0, LEAST(1.0, raw_y))) AS bbox_height
FROM validated;

\echo 'REAL/SUCCEEDED 视觉结果总览：'
SELECT
    COUNT(*) AS result_count,
    COUNT(*) FILTER (WHERE canonical_source='STRUCTURED_RESULT') AS structured_result_count,
    COUNT(*) FILTER (WHERE canonical_source='RAW_OUTPUT_SNAPSHOT') AS raw_output_snapshot_count,
    COUNT(*) FILTER (WHERE canonical_source='NONE') AS no_model_detection_snapshot_count
FROM tmp_ai_detection_inventory;

\echo 'REAL 视觉检测数量不一致预览：'
WITH per_inference AS (
    SELECT
        i.inference_id,
        i.asset_id,
        i.building_id,
        i.review_status,
        i.result_id,
        i.canonical_source,
        jsonb_array_length(i.canonical_detections) AS canonical_count,
        (SELECT COUNT(*) FROM ai.detection d WHERE d.inference_result_id=i.result_id) AS db_count,
        (SELECT COUNT(*) FROM tmp_ai_detection_repair_candidates c
         WHERE c.result_id=i.result_id AND c.repairable) AS repairable_count,
        (SELECT COUNT(*) FROM tmp_ai_detection_repair_candidates c
         WHERE c.result_id=i.result_id
           AND c.repairable
           AND NOT EXISTS (
               SELECT 1 FROM ai.detection d
               WHERE d.inference_result_id=i.result_id
                 AND d.sequence_no=c.sequence_no
           )) AS missing_repairable_count
    FROM tmp_ai_detection_inventory i
)
SELECT
    inference_id,
    asset_id,
    building_id,
    review_status,
    canonical_source,
    canonical_count,
    db_count,
    repairable_count,
    missing_repairable_count,
    CASE
      WHEN canonical_count < db_count THEN 'MANUAL_REVIEW_DB_HAS_EXTRA_ROWS'
      WHEN canonical_count > db_count AND missing_repairable_count = 0 THEN 'MANUAL_REVIEW_NO_SAFE_INSERT'
      WHEN review_status IN ('CONFIRMED','CORRECTED') AND missing_repairable_count > 0
        THEN 'REPAIRABLE_AND_FORMAL_ASSESSMENT_WILL_BE_STALED'
      WHEN missing_repairable_count > 0 THEN 'REPAIRABLE'
      ELSE 'MANUAL_REVIEW'
    END AS action
FROM per_inference
WHERE canonical_count <> db_count
ORDER BY missing_repairable_count DESC, inference_id;

\if :apply
\echo 'APPLY=true：开始幂等补齐缺失的 REAL ai.detection 投影...'
BEGIN;

-- 在 INSERT 前冻结真正会新增明细、且已进入正式评分资格的受影响楼栋。
CREATE TEMP TABLE tmp_ai_detection_repair_affected_buildings AS
SELECT DISTINCT c.building_id
FROM tmp_ai_detection_repair_candidates c
WHERE c.building_id IS NOT NULL
  AND c.review_status IN ('CONFIRMED','CORRECTED')
  AND c.repairable
  AND c.bbox_width > 0
  AND c.bbox_height > 0
  AND NOT EXISTS (
      SELECT 1 FROM ai.detection d
      WHERE d.inference_result_id=c.result_id
        AND d.sequence_no=c.sequence_no
  );

INSERT INTO ai.detection
    (id, inference_result_id, sequence_no, class_code, class_name, confidence,
     bbox_x, bbox_y, bbox_width, bbox_height, coordinate_type, extra_data)
SELECT
    gen_random_uuid(),
    c.result_id,
    c.sequence_no,
    COALESCE(NULLIF(c.detection->>'classCode',''), 'UNKNOWN'),
    COALESCE(NULLIF(c.detection->>'className',''), '未分类候选'),
    GREATEST(0.0, LEAST(1.0,
        CASE WHEN jsonb_typeof(c.detection->'confidence')='number'
             THEN (c.detection->>'confidence')::double precision ELSE 0.0 END)),
    c.bbox_x,
    c.bbox_y,
    c.bbox_width,
    c.bbox_height,
    COALESCE(NULLIF(c.detection->'boundingBox'->>'coordinateType',''), 'NORMALIZED_XYWH'),
    '{}'::jsonb
FROM tmp_ai_detection_repair_candidates c
WHERE c.repairable
  AND c.bbox_width > 0
  AND c.bbox_height > 0
  AND NOT EXISTS (
      SELECT 1 FROM ai.detection d
      WHERE d.inference_result_id=c.result_id
        AND d.sequence_no=c.sequence_no
  );

-- 与现有人工复核治理语义一致：证据变化后只让旧正式结果失效，不自动重算。
UPDATE core.completeness_assessment a
SET status='STALE', stale_reason='AI_REVIEW_CHANGED'
FROM tmp_ai_detection_repair_affected_buildings b
WHERE a.building_id=b.building_id
  AND a.status='CURRENT';

UPDATE core.risk_assessment a
SET status='STALE', stale_reason='AI_REVIEW_CHANGED', updated_at=CURRENT_TIMESTAMP
FROM tmp_ai_detection_repair_affected_buildings b
WHERE a.building_id=b.building_id
  AND a.status='CURRENT';

UPDATE core.renewal_priority a
SET status='STALE', stale_reason='AI_REVIEW_CHANGED'
FROM tmp_ai_detection_repair_affected_buildings b
WHERE a.building_id=b.building_id
  AND a.status='CURRENT';

\echo '因已确认/修正 REAL 证据被补齐而需要显式重新评估的楼栋：'
SELECT building_id
FROM tmp_ai_detection_repair_affected_buildings
ORDER BY building_id;

\echo '补齐后仍存在数量不一致的 REAL 结果：'
SELECT
    i.inference_id,
    i.canonical_source,
    jsonb_array_length(i.canonical_detections) AS canonical_count,
    (SELECT COUNT(*) FROM ai.detection d WHERE d.inference_result_id=i.result_id) AS db_count
FROM tmp_ai_detection_inventory i
WHERE jsonb_array_length(i.canonical_detections)
      <> (SELECT COUNT(*) FROM ai.detection d WHERE d.inference_result_id=i.result_id)
ORDER BY i.inference_id;
COMMIT;
\echo '修复完成。被列出的楼栋正式评分已标记 STALE，请通过系统显式重新评估；脚本不会自动改分。'
\else
\echo '当前为 DRY-RUN，只读预览；如确认无误，请依据 action 决定是否使用 --apply。'
\endif
