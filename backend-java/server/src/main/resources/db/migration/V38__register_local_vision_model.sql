-- UrbanSafe Priority Flyway migration V38
-- 登记本地零样本视觉模型（Grounding DINO Base + SAM2.1 Base+）。
--
-- V37 已登记 LOCAL-VISION-ACCURACY-001 工作流并把 model_code 指向 AI-VISION-LOCAL-001，
-- 同时 AiAutomationSettings 的默认 modelId 也指向 AI-VISION-LOCAL-001；但 model_registry
-- 一直缺少该模型的准入元数据，导致图片上传自动识别（ACCURACY）异步执行时报
-- “ACCURACY 请求模型未登记”而失败。本迁移补齐该模型登记，使本地视觉自动识别主链可用。

INSERT INTO ai.model_registry (
    model_code,
    model_name,
    model_version,
    model_type,
    framework,
    source_type,
    license_name,
    input_spec,
    output_spec,
    model_config,
    status,
    mode,
    source_platform,
    source_resource_id,
    source_revision,
    weight_filename,
    weight_sha256,
    supported_classes,
    limitations,
    approved_at,
    deployment_stage,
    formal_evidence_enabled,
    quality_status,
    quality_summary,
    quality_evaluated_at
)
VALUES (
    'AI-VISION-LOCAL-001',
    'UrbanSafe Grounding DINO Base + SAM2.1 Base+ 零样本建筑表观病害',
    '1.1.0',
    'OBJECT_DETECTION',
    'PyTorch-CUDA',
    'ZERO_SHOT_OPEN_WEIGHTS',
    'Apache-2.0',
    jsonb_build_object(
        'contentTypes', jsonb_build_array('image/jpeg', 'image/png', 'image/webp'),
        'maxLongSide', 1280,
        'boxThreshold', 0.25,
        'textThreshold', 0.25,
        'maxDetections', 10,
        'runtime', 'CUDA_ONLY'
    ),
    jsonb_build_object(
        'coordinateType', 'NORMALIZED_XYWH',
        'classes', jsonb_build_array('CRACK', 'SPALLING', 'EXPOSED_REBAR', 'CORROSION', 'WATER_STAIN', 'SURFACE_DAMAGE')
    ),
    jsonb_build_object(
        'adapter', 'grounded-sam2-v1',
        'executionProvider', 'PyTorch-CUDA',
        'runtimeCatalogRequired', true,
        'professionalReviewRequired', true,
        'offline', true,
        'cudaRequired', true
    ),
    'APPROVED',
    'REAL',
    'Hugging Face / ModelScope',
    'AI-VISION-LOCAL-001/1.1.0',
    '12bdfa3120f3e7ec7b434d90674b3396eccf88eb',
    'model.safetensors',
    '0f2e2b52a28f6ab7ad6f0ac1b2815c895fe44edb0d5763e67d3731fc23e7adde',
    jsonb_build_array('CRACK', 'SPALLING', 'EXPOSED_REBAR', 'CORROSION', 'WATER_STAIN', 'SURFACE_DAMAGE'),
    jsonb_build_array(
        '真实模型结果必须经专业人员复核',
        '模型置信度不代表房屋危险概率',
        '不作为正式房屋安全鉴定结论'
    ),
    TIMESTAMPTZ '2026-08-14 03:55:59+00',
    'DEMO',
    FALSE,
    'VALIDATING',
    jsonb_build_object('reason', '本地零样本视觉模型，比赛演示用，尚未完成独立质量验收'),
    CURRENT_TIMESTAMP
)
ON CONFLICT (model_code) WHERE deleted_at IS NULL DO UPDATE SET
    model_name = EXCLUDED.model_name,
    model_version = EXCLUDED.model_version,
    model_type = EXCLUDED.model_type,
    framework = EXCLUDED.framework,
    source_type = EXCLUDED.source_type,
    license_name = EXCLUDED.license_name,
    input_spec = EXCLUDED.input_spec,
    output_spec = EXCLUDED.output_spec,
    model_config = EXCLUDED.model_config,
    status = EXCLUDED.status,
    mode = EXCLUDED.mode,
    source_platform = EXCLUDED.source_platform,
    source_resource_id = EXCLUDED.source_resource_id,
    source_revision = EXCLUDED.source_revision,
    weight_filename = EXCLUDED.weight_filename,
    weight_sha256 = EXCLUDED.weight_sha256,
    supported_classes = EXCLUDED.supported_classes,
    limitations = EXCLUDED.limitations,
    approved_at = EXCLUDED.approved_at,
    deployment_stage = EXCLUDED.deployment_stage,
    formal_evidence_enabled = EXCLUDED.formal_evidence_enabled,
    quality_status = EXCLUDED.quality_status,
    quality_summary = EXCLUDED.quality_summary,
    quality_evaluated_at = EXCLUDED.quality_evaluated_at,
    updated_at = CURRENT_TIMESTAMP;
