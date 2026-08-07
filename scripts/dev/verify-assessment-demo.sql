-- 第四阶段评分演示验证：验证正式评分接口生成的结果。
\set ON_ERROR_STOP on

WITH demo_buildings AS (
    SELECT b.id, b.building_code
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
    WHERE c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
      AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02')
      AND b.deleted_at IS NULL
), counts AS (
    SELECT
      (SELECT count(*) FROM core.completeness_assessment ca JOIN demo_buildings d ON d.id=ca.building_id WHERE ca.status='CURRENT' AND ca.engine_version='phase4-rule-engine-v1') AS completeness_count,
      (SELECT count(*) FROM core.risk_assessment ra JOIN demo_buildings d ON d.id=ra.building_id WHERE ra.status='CURRENT' AND ra.engine_version='phase4-rule-engine-v1') AS risk_count,
      (SELECT count(*) FROM core.renewal_priority rp JOIN demo_buildings d ON d.id=rp.building_id WHERE rp.status='CURRENT' AND rp.engine_version='phase4-rule-engine-v1' AND rp.ranking_scope_key='ALL') AS priority_count,
      (SELECT count(*) FROM ai.inference_task t JOIN demo_buildings d ON d.id=t.building_id WHERE t.mode='MOCK' AND t.status='SUCCEEDED') AS mock_input_count,
      (SELECT count(*) FROM ai.inference_task t JOIN demo_buildings d ON d.id=t.building_id WHERE t.mode='REAL' AND t.status='SUCCEEDED' AND t.review_status IN ('CONFIRMED','CORRECTED')) AS reviewed_real_count
)
SELECT * FROM counts;

DO $$
DECLARE c integer; r integer; p integer; real_count integer; mock_count integer; excluded_count integer;
BEGIN
    WITH demo_buildings AS (
        SELECT b.id, b.building_code
        FROM core.building b
        JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
        WHERE c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
          AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02')
          AND b.deleted_at IS NULL
    )
    SELECT
      (SELECT count(*) FROM core.completeness_assessment ca JOIN demo_buildings d ON d.id=ca.building_id WHERE ca.status='CURRENT' AND ca.engine_version='phase4-rule-engine-v1'),
      (SELECT count(*) FROM core.risk_assessment ra JOIN demo_buildings d ON d.id=ra.building_id WHERE ra.status='CURRENT' AND ra.engine_version='phase4-rule-engine-v1'),
      (SELECT count(*) FROM core.renewal_priority rp JOIN demo_buildings d ON d.id=rp.building_id WHERE rp.status='CURRENT' AND rp.engine_version='phase4-rule-engine-v1' AND rp.ranking_scope_key='ALL'),
      (SELECT count(*) FROM ai.inference_task t JOIN demo_buildings d ON d.id=t.building_id WHERE t.mode='REAL' AND t.status='SUCCEEDED' AND t.review_status IN ('CONFIRMED','CORRECTED')),
      (SELECT count(*) FROM ai.inference_task t JOIN demo_buildings d ON d.id=t.building_id WHERE t.mode='MOCK' AND t.status='SUCCEEDED')
    INTO c, r, p, real_count, mock_count;
    IF c<>5 OR r<>5 OR p<>5 THEN
        RAISE EXCEPTION '正式评分结果数量异常：completeness=%, risk=%, priority=%', c, r, p;
    END IF;
    IF real_count<1 OR mock_count<1 THEN
        RAISE EXCEPTION 'AI 输入覆盖异常：reviewedReal=%, mock=%', real_count, mock_count;
    END IF;

    WITH demo_buildings AS (
        SELECT b.id, b.building_code
        FROM core.building b
        JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
        WHERE c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
          AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02')
          AND b.deleted_at IS NULL
    )
    SELECT count(*) INTO excluded_count
    FROM core.risk_assessment ra
    JOIN demo_buildings d ON d.id=ra.building_id
    WHERE d.building_code='A-03'
      AND ra.status='CURRENT'
      AND (
          EXISTS (
              SELECT 1
              FROM jsonb_array_elements(COALESCE(ra.score_explanation->'excludedEvidence', '[]'::jsonb)) excluded
              WHERE excluded->>'sourceType'='AI_INFERENCE'
                AND excluded->>'reason' LIKE '%模拟%'
          )
          OR EXISTS (
              SELECT 1
              FROM jsonb_array_elements(COALESCE(ra.score_explanation->'topFactors', '[]'::jsonb)) factor
              WHERE factor->>'factorCode'='AI_EXCLUDED'
                AND factor->>'direction'='EXCLUDED'
          )
      );
    IF excluded_count < 1 THEN
        RAISE EXCEPTION 'A-03 未记录 MOCK AI 排除证据，请检查评分解释';
    END IF;
END $$;
