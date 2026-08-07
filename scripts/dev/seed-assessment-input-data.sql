-- 第四阶段评分输入演示数据。
-- 只写入评分引擎输入，不直接写 core.completeness_assessment、core.risk_assessment 或 core.renewal_priority。
BEGIN;

DO $$
DECLARE n integer;
BEGIN
    SELECT count(*) INTO n
    FROM core.building b
    JOIN core.community c ON c.id=b.community_id
    WHERE c.community_code IN ('DEMO-COMMUNITY-001','DEMO-COMMUNITY-002')
      AND b.building_code IN ('A-01','A-02','A-03','B-01','B-02')
      AND b.deleted_at IS NULL AND c.deleted_at IS NULL;
    IF n <> 5 THEN
        RAISE EXCEPTION '第四阶段评分输入依赖 5 栋 DEMO 楼栋，实际 % 栋，请先执行 seed-demo-data.sql', n;
    END IF;
END $$;

WITH evidence_data(
    id, community_code, building_code, evidence_type, title, description,
    occurred_at, source, reliability_level, evidence_data
) AS (
    VALUES
        ('44000000-0000-4000-8000-000000000001'::uuid, 'DEMO-COMMUNITY-002', 'B-01', 'PUBLIC_VALUE', '养老服务站与沿街公共服务设施', '楼栋所在街坊承担社区养老服务和便民商业功能。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '30 days', '社区更新摸排台账', 'OFFICIAL_RECORD', '{"score":92,"demo":true}'::jsonb),
        ('44000000-0000-4000-8000-000000000002'::uuid, 'DEMO-COMMUNITY-002', 'B-01', 'GOVERNANCE_URGENCY', '违规改造治理台账', '首层存在历史改造和群众反映集中问题，需纳入近期治理评估。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '20 days', '街道治理台账', 'OFFICIAL_RECORD', '{"score":88,"demo":true}'::jsonb),
        ('44000000-0000-4000-8000-000000000003'::uuid, 'DEMO-COMMUNITY-001', 'A-03', 'GOVERNANCE_URGENCY', '低完整度重点复核清单', '资料不足且现场病害较重，需先复核再进入治理排序。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '15 days', '社区巡查台账', 'OFFICIAL_RECORD', '{"score":72,"demo":true}'::jsonb),
        ('44000000-0000-4000-8000-000000000004'::uuid, 'DEMO-COMMUNITY-001', 'A-02', 'PUBLIC_VALUE', '常规居住楼栋', '公共服务属性一般，作为低风险资料完整样例。', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '40 days', '社区更新摸排台账', 'OFFICIAL_RECORD', '{"score":20,"demo":true}'::jsonb)
)
INSERT INTO core.building_evidence (
    id, building_id, evidence_type, title, description, occurred_at,
    source, reliability_level, evidence_data, created_by
)
SELECT data.id, b.id, data.evidence_type, data.title, data.description,
       data.occurred_at, data.source, data.reliability_level, data.evidence_data, u.id
FROM evidence_data data
JOIN core.community c ON c.community_code=data.community_code AND c.deleted_at IS NULL
JOIN core.building b ON b.community_id=c.id AND b.building_code=data.building_code AND b.deleted_at IS NULL
JOIN core.user_account u ON u.username='demo_community' AND u.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    building_id=EXCLUDED.building_id,
    evidence_type=EXCLUDED.evidence_type,
    title=EXCLUDED.title,
    description=EXCLUDED.description,
    occurred_at=EXCLUDED.occurred_at,
    source=EXCLUDED.source,
    reliability_level=EXCLUDED.reliability_level,
    evidence_data=EXCLUDED.evidence_data,
    created_by=EXCLUDED.created_by,
    updated_at=TIMESTAMPTZ '2026-07-25 12:00:00+08',
    deleted_at=NULL;

INSERT INTO ai.model_registry (
    id, model_code, model_name, model_version, model_type, framework,
    source_type, license_name, input_spec, output_spec, model_config,
    status, mode, source_platform, source_resource_id, source_revision,
    weight_filename, weight_sha256, supported_classes, limitations, approved_at
) VALUES (
    '54000000-0000-4000-8000-000000000001'::uuid,
    'AI-DEFECT-DEMO-REAL-001',
    'UrbanSafe Demo Approved Real Detector',
    '1.0.0-demo',
    'OBJECT_DETECTION',
    'DEMO_OFFLINE_FIXTURE',
    'LOCAL_APPROVED_FIXTURE',
    'PROJECT-INTERNAL-DEMO',
    '{"contentTypes":["image/jpeg"],"offline":true}'::jsonb,
    '{"coordinateType":"NORMALIZED_XYWH","classes":["CRACK","SURFACE_FALLING"]}'::jsonb,
    '{"demo":true,"runtimeDownload":false}'::jsonb,
    'APPROVED',
    'REAL',
    'LOCAL_FIXTURE',
    'DEMO-AI-RESOURCE',
    '2026-07-25',
    'demo-real-detector.onnx',
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    '["CRACK","SURFACE_FALLING"]'::jsonb,
    '["演示夹具，不代表正式模型能力","模型置信度不等于房屋危险概率"]'::jsonb,
    TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '1 day'
)
ON CONFLICT (model_code) WHERE deleted_at IS NULL DO UPDATE SET
    status=EXCLUDED.status,
    mode=EXCLUDED.mode,
    approved_at=EXCLUDED.approved_at,
    updated_at=TIMESTAMPTZ '2026-07-25 12:00:00+08';

WITH asset_data(id, object_key, original_filename, sha256, business_code) AS (
    VALUES
        ('55000000-0000-4000-8000-000000000001'::uuid, 'demo/phase4/b01-real-confirmed.jpg', 'b01-real-confirmed.jpg', 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb', 'B-01'),
        ('55000000-0000-4000-8000-000000000002'::uuid, 'demo/phase4/a03-mock-only.jpg', 'a03-mock-only.jpg', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc', 'A-03')
)
INSERT INTO asset.file_asset (
    id, bucket_name, object_key, original_filename, content_type,
    file_size, sha256, business_type, business_id, upload_status,
    uploaded_by, metadata
)
SELECT data.id, 'urban-safe-assets', data.object_key, data.original_filename,
       'image/jpeg', 1024, data.sha256, 'INSPECTION_IMAGE', b.id,
       'AVAILABLE', u.id, jsonb_build_object('demo', true, 'phase', 4)
FROM asset_data data
JOIN core.building b ON b.building_code=data.business_code AND b.deleted_at IS NULL
JOIN core.user_account u ON u.username='demo_inspector' AND u.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    bucket_name=EXCLUDED.bucket_name,
    object_key=EXCLUDED.object_key,
    original_filename=EXCLUDED.original_filename,
    content_type=EXCLUDED.content_type,
    file_size=EXCLUDED.file_size,
    business_type=EXCLUDED.business_type,
    business_id=EXCLUDED.business_id,
    upload_status=EXCLUDED.upload_status,
    uploaded_by=EXCLUDED.uploaded_by,
    metadata=EXCLUDED.metadata,
    updated_at=TIMESTAMPTZ '2026-07-25 12:00:00+08',
    deleted_at=NULL;

WITH task_data(id, request_code, asset_id, community_code, building_code, model_code, mode, review_status) AS (
    VALUES
        ('56000000-0000-4000-8000-000000000001'::uuid, 'DEMO-AI-REAL-B01-001', '55000000-0000-4000-8000-000000000001'::uuid, 'DEMO-COMMUNITY-002', 'B-01', 'AI-DEFECT-DEMO-REAL-001', 'REAL', 'CONFIRMED'),
        ('56000000-0000-4000-8000-000000000002'::uuid, 'DEMO-AI-MOCK-A03-001', '55000000-0000-4000-8000-000000000002'::uuid, 'DEMO-COMMUNITY-001', 'A-03', 'AI-DEFECT-MOCK-001', 'MOCK', 'CONFIRMED')
)
INSERT INTO ai.inference_task (
    id, request_code, idempotency_key, asset_id, building_id, community_id,
    model_registry_id, mode, status, attempt_no, review_status, requested_by,
    requested_at, started_at, completed_at, duration_ms
)
SELECT data.id, data.request_code, data.request_code, data.asset_id, b.id, c.id,
       m.id, data.mode, 'SUCCEEDED', 1, data.review_status, u.id,
       TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '12 hours', TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '12 hours',
       TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '12 hours' + INTERVAL '2 seconds', 2000
FROM task_data data
JOIN core.community c ON c.community_code=data.community_code AND c.deleted_at IS NULL
JOIN core.building b ON b.community_id=c.id AND b.building_code=data.building_code AND b.deleted_at IS NULL
JOIN ai.model_registry m ON m.model_code=data.model_code AND m.deleted_at IS NULL
JOIN core.user_account u ON u.username='demo_expert' AND u.deleted_at IS NULL
ON CONFLICT (request_code) DO UPDATE SET
    idempotency_key=EXCLUDED.idempotency_key,
    asset_id=EXCLUDED.asset_id,
    building_id=EXCLUDED.building_id,
    community_id=EXCLUDED.community_id,
    model_registry_id=EXCLUDED.model_registry_id,
    mode=EXCLUDED.mode,
    status=EXCLUDED.status,
    attempt_no=EXCLUDED.attempt_no,
    review_status=EXCLUDED.review_status,
    requested_by=EXCLUDED.requested_by,
    requested_at=EXCLUDED.requested_at,
    started_at=EXCLUDED.started_at,
    completed_at=EXCLUDED.completed_at,
    duration_ms=EXCLUDED.duration_ms,
    updated_at=TIMESTAMPTZ '2026-07-25 12:00:00+08';

WITH result_data(id, task_id, summary) AS (
    VALUES
        ('57000000-0000-4000-8000-000000000001'::uuid, '56000000-0000-4000-8000-000000000001'::uuid, '{"part":"西侧外墙","demo":true}'::jsonb),
        ('57000000-0000-4000-8000-000000000002'::uuid, '56000000-0000-4000-8000-000000000002'::uuid, '{"part":"首层外墙","demo":true}'::jsonb)
)
INSERT INTO ai.inference_result (
    id, inference_task_id, image_width, image_height, quality_status,
    applicability, summary, raw_output_snapshot, warning_messages
)
SELECT id, task_id, 1280, 720, 'OK', 'APPLICABLE', summary,
       jsonb_build_object('demo', true), '[]'::jsonb
FROM result_data
ON CONFLICT (inference_task_id) DO UPDATE SET
    image_width=EXCLUDED.image_width,
    image_height=EXCLUDED.image_height,
    quality_status=EXCLUDED.quality_status,
    applicability=EXCLUDED.applicability,
    summary=EXCLUDED.summary,
    raw_output_snapshot=EXCLUDED.raw_output_snapshot,
    warning_messages=EXCLUDED.warning_messages;

DELETE FROM ai.detection WHERE inference_result_id IN (
    '57000000-0000-4000-8000-000000000001'::uuid,
    '57000000-0000-4000-8000-000000000002'::uuid
);

INSERT INTO ai.detection (
    id, inference_result_id, sequence_no, class_code, class_name, confidence,
    bbox_x, bbox_y, bbox_width, bbox_height, extra_data
) VALUES
    ('58000000-0000-4000-8000-000000000001'::uuid, '57000000-0000-4000-8000-000000000001'::uuid, 1, 'CRACK', '裂缝', 0.91000, 0.10000, 0.12000, 0.30000, 0.18000, '{"severity":"SEVERE","part":"西侧外墙"}'::jsonb),
    ('58000000-0000-4000-8000-000000000002'::uuid, '57000000-0000-4000-8000-000000000001'::uuid, 2, 'SURFACE_FALLING', '饰面脱落', 0.82000, 0.55000, 0.30000, 0.18000, 0.14000, '{"severity":"MEDIUM","part":"西侧外墙"}'::jsonb),
    ('58000000-0000-4000-8000-000000000003'::uuid, '57000000-0000-4000-8000-000000000002'::uuid, 1, 'CRACK', '裂缝', 0.99000, 0.12000, 0.18000, 0.26000, 0.12000, '{"severity":"SEVERE","part":"首层外墙"}'::jsonb);

INSERT INTO ai.inference_review (
    id, inference_task_id, review_status, review_comment, reviewed_by,
    reviewed_at, corrected_data
)
SELECT '59000000-0000-4000-8000-000000000001'::uuid,
       '56000000-0000-4000-8000-000000000001'::uuid,
       'CONFIRMED', '演示：真实模型结果已由专家确认。', u.id,
       TIMESTAMPTZ '2026-07-25 12:00:00+08' - INTERVAL '11 hours', '{}'::jsonb
FROM core.user_account u WHERE u.username='demo_expert' AND u.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
    review_status=EXCLUDED.review_status,
    review_comment=EXCLUDED.review_comment,
    reviewed_by=EXCLUDED.reviewed_by,
    reviewed_at=EXCLUDED.reviewed_at,
    corrected_data=EXCLUDED.corrected_data;

COMMIT;
