-- UrbanSafe Priority 比赛演示知识数据。
-- 仅用于本项目内部演示，不冒充国家标准、地方规范或法定鉴定文件。
-- 文档统一标记为 ACTIVE + INTERNAL，并仅向 ADMIN / PROPERTY_INSPECTOR / EXPERT 开放。

BEGIN;

DO $$
BEGIN
    IF to_regclass('knowledge.document') IS NULL
       OR to_regclass('knowledge.chunk') IS NULL THEN
        RAISE EXCEPTION '知识库表尚未完成迁移，请先启动后端并让 Flyway 执行到 V32 及以后版本';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM core.user_account
        WHERE username='demo_admin' AND deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION '缺少 demo_admin，请先执行 bash scripts/dev/seed-demo-data.sh';
    END IF;
END $$;

WITH admin_user AS (
    SELECT id FROM core.user_account
    WHERE username='demo_admin' AND deleted_at IS NULL
    LIMIT 1
), document_data(document_code, title, document_type, document_version, source_uri, content_seed) AS (
    VALUES
      ('DEMO-KNOWLEDGE-CRACK-001', '城安智序内部演示指南：建筑外墙裂缝巡检', 'INTERNAL_GUIDE', '1.0.0', 'internal://demo/knowledge/exterior-wall-crack',
       '建筑外墙裂缝巡检：位置、走向、宽度、长度、贯通、渗水、远景、近景、楼层、构件。'),
      ('DEMO-KNOWLEDGE-FALLING-001', '城安智序内部演示指南：外墙饰面脱落处置', 'INTERNAL_GUIDE', '1.0.0', 'internal://demo/knowledge/surface-falling',
       '外墙饰面脱落：警戒隔离、范围、空鼓、松动、坠落风险、维修、整改、复验。'),
      ('DEMO-KNOWLEDGE-WATER-001', '城安智序内部演示指南：渗水问题巡检', 'INTERNAL_GUIDE', '1.0.0', 'internal://demo/knowledge/water-leakage',
       '渗水巡检：位置、雨后、水迹、霉变、裂缝、管线、范围、持续观察。'),
      ('DEMO-KNOWLEDGE-AI-BOUNDARY-001', '城安智序内部演示指南：AI辅助与专业责任边界', 'INTERNAL_POLICY', '1.0.0', 'internal://demo/knowledge/ai-responsibility',
       'AI辅助复核不直接修改正式风险评分，正式评分由确定性规则计算，人工专业复核保留最终业务责任。'),
      ('DEMO-KNOWLEDGE-CLOSURE-001', '城安智序内部演示指南：整改处置与复查复验闭环', 'INTERNAL_PROCEDURE', '1.0.0', 'internal://demo/knowledge/rectification-reinspection',
       '整改完成待复验、创建复查任务、复验通过关闭、复验不通过退回整改、重新评分。'),
      ('DEMO-KNOWLEDGE-EVIDENCE-001', '城安智序内部演示指南：现场证据采集', 'INTERNAL_GUIDE', '1.0.0', 'internal://demo/knowledge/evidence-capture',
       '现场证据采集：全景、中景、近景、位置、时间、构件、尺度参照、原图、整改前后对照。')
)
INSERT INTO knowledge.document (
    id, document_code, title, document_type, document_version, status,
    security_level, role_scope, source_uri, content_checksum,
    effective_from, effective_to, metadata, created_by, created_at, updated_at
)
SELECT
    gen_random_uuid(), d.document_code, d.title, d.document_type, d.document_version, 'ACTIVE',
    'INTERNAL', ARRAY['ADMIN','PROPERTY_INSPECTOR','EXPERT']::TEXT[], d.source_uri,
    md5(d.content_seed), CURRENT_TIMESTAMP - INTERVAL '1 day', NULL,
    jsonb_build_object(
        'demo', true,
        'reviewStatus', 'APPROVED',
        'reviewedFor', 'COMPETITION_DEMO',
        'sourceType', 'INTERNAL_DEMO_GUIDE',
        'legalDisclaimer', '项目内部演示知识，不替代国家标准、地方规范或专业鉴定文件'
    ),
    a.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM document_data d CROSS JOIN admin_user a
ON CONFLICT (document_code, document_version) DO UPDATE SET
    title=EXCLUDED.title,
    document_type=EXCLUDED.document_type,
    status='ACTIVE',
    security_level='INTERNAL',
    role_scope=EXCLUDED.role_scope,
    source_uri=EXCLUDED.source_uri,
    content_checksum=EXCLUDED.content_checksum,
    effective_from=EXCLUDED.effective_from,
    effective_to=NULL,
    metadata=EXCLUDED.metadata,
    updated_at=CURRENT_TIMESTAMP;

WITH chunks(document_code, chunk_index, section_title, content) AS (
    VALUES
      ('DEMO-KNOWLEDGE-CRACK-001', 0, '建筑外墙发现裂缝时应记录什么',
       '建筑外墙发现裂缝时，现场巡检应重点记录裂缝所在楼层和构件位置、裂缝走向、可测量的宽度与长度、是否贯通、是否伴随渗水或表面剥落。照片应至少包含能确认位置的远景、体现病害范围的中景和带尺度参照的近景。无法判断裂缝性质时，应提交人工专业复核，不得仅依据照片形成安全鉴定结论。'),
      ('DEMO-KNOWLEDGE-CRACK-001', 1, '裂缝证据补充建议',
       '疑似裂缝证据不足时，建议补充同一位置不同距离照片、尺度参照、楼层和墙面方位，并记录是否在雨后扩大、是否存在渗水、空鼓或剥落。复巡时应尽量保持相同拍摄位置，便于人工比较变化。'),

      ('DEMO-KNOWLEDGE-FALLING-001', 0, '外墙饰面脱落如何处置',
       '发现外墙饰面脱落、松动或可能坠落时，应先关注人员和车辆暴露区域，必要时设置警戒或临时隔离，再记录脱落位置、范围、松动区域、空鼓迹象及现场照片。整改完成后不能直接视为治理闭环，应保留整改前后证据并安排复查复验。'),
      ('DEMO-KNOWLEDGE-FALLING-001', 1, '饰面整改后的复验重点',
       '外墙饰面整改后的复验应核对原问题位置是否已处理、周边是否仍有松动或新脱落、警戒措施是否可以解除，并采集整改后全景与近景。复验未通过时应退回继续整改。'),

      ('DEMO-KNOWLEDGE-WATER-001', 0, '渗水现场巡检记录',
       '渗水问题巡检应记录具体位置、出现时间、是否与降雨相关、水迹和霉变范围、附近裂缝或管线情况，并拍摄能够确认空间位置和渗水范围的照片。条件允许时应在后续降雨后复巡，避免把一次性表面水迹直接解释为结构安全结论。'),

      ('DEMO-KNOWLEDGE-AI-BOUNDARY-001', 0, 'AI可以做什么',
       'AI视觉识别负责发现疑似病害区域，AI综合研判负责解释、总结和提出排序建议。AI结果属于辅助信息，不直接覆盖人工专业复核，不直接修改正式风险分数，也不能代替房屋安全鉴定。'),
      ('DEMO-KNOWLEDGE-AI-BOUNDARY-001', 1, '正式风险评分责任边界',
       '正式风险评分应继续由系统确定性评分规则基于已确认业务数据计算。人工复核可以确认、修正或驳回AI发现，也可以记录辅助风险度，但正式风险分必须通过正式评分链重新计算。'),

      ('DEMO-KNOWLEDGE-CLOSURE-001', 0, '处置整改和复查复验如何闭环',
       '治理闭环建议采用：问题进入处理中状态，处置人员提交整改说明和整改证据；整改完成后状态进入“已整改待复验”；系统创建复查复验巡检任务；巡检人员完成现场复查记录。复验通过后工单关闭，复验不通过则退回处理中继续整改。'),
      ('DEMO-KNOWLEDGE-CLOSURE-001', 1, '复验通过后下一步',
       '复验通过只表示该次整改事项完成闭环。若复查产生新的有效巡检证据，已有正式风险评分或报告可能需要重新计算或更新。AI不能因为复验通过而自动降低正式风险分。'),

      ('DEMO-KNOWLEDGE-EVIDENCE-001', 0, '现场照片如何采集',
       '现场照片建议形成全景、中景、近景组合：全景用于确认楼栋和方位，中景用于确认构件及病害范围，近景用于观察细部并尽量加入尺度参照。原始图片应保留，AI Polygon属于辅助标注，人工复核时仍应能够回看原图。'),
      ('DEMO-KNOWLEDGE-EVIDENCE-001', 1, '整改前后证据要求',
       '整改事项建议保存整改前、整改过程和整改后证据。复查复验时优先使用与整改前相近的拍摄位置和尺度，并在巡检记录中写明是否通过以及仍需整改的内容。')
)
INSERT INTO knowledge.chunk AS existing (
    id, document_id, chunk_index, section_title, page_number, content, metadata
)
SELECT
    gen_random_uuid(), d.id, c.chunk_index, c.section_title, NULL, c.content,
    jsonb_build_object('demo', true, 'reviewStatus', 'APPROVED')
FROM chunks c
JOIN knowledge.document d
  ON d.document_code=c.document_code AND d.document_version='1.0.0'
ON CONFLICT (document_id, chunk_index) DO UPDATE SET
    section_title=EXCLUDED.section_title,
    page_number=EXCLUDED.page_number,
    content=EXCLUDED.content,
    metadata=EXCLUDED.metadata,
    embedding=CASE
        WHEN existing.section_title IS DISTINCT FROM EXCLUDED.section_title
          OR existing.content IS DISTINCT FROM EXCLUDED.content
        THEN NULL
        ELSE existing.embedding
    END;

COMMIT;

SELECT d.document_code, d.title, d.status, count(c.id) AS chunk_count
FROM knowledge.document d
JOIN knowledge.chunk c ON c.document_id=d.id
WHERE d.document_code LIKE 'DEMO-KNOWLEDGE-%'
  AND d.document_version='1.0.0'
GROUP BY d.document_code, d.title, d.status
ORDER BY d.document_code;
