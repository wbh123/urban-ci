-- UrbanSafe Priority Flyway migration V39
-- 1. 将 AI-BUILDING-YOLOX-001 登记为业务侧 APPROVED，但继续保持 VALIDATING，
--    未安装真实权重时不得进入正式证据或被选为运行默认模型。
-- 2. 将默认视觉模型从代码常量迁移到 ai.governance_setting 持久化。

ALTER TABLE ai.governance_setting
    ADD COLUMN text_value VARCHAR(128);

ALTER TABLE ai.governance_setting
    ALTER COLUMN boolean_value DROP NOT NULL;

ALTER TABLE ai.governance_setting
    ADD CONSTRAINT ck_ai_governance_setting_exactly_one_value
        CHECK (
            (boolean_value IS NOT NULL AND text_value IS NULL)
            OR (boolean_value IS NULL AND text_value IS NOT NULL)
        );

INSERT INTO ai.governance_setting (setting_key, boolean_value, text_value)
VALUES ('DEFAULT_VISION_MODEL_ID', NULL, 'AI-VISION-LOCAL-001')
ON CONFLICT (setting_key) DO NOTHING;

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
    'AI-BUILDING-YOLOX-001',
    'YOLOX-S 建筑病害检测',
    '1.0.0',
    'OBJECT_DETECTION',
    'ONNXRuntime-GPU',
    'PROJECT_TRAINING_CANDIDATE',
    'Apache-2.0',
    jsonb_build_object(
        'contentTypes', jsonb_build_array('image/jpeg', 'image/png', 'image/webp'),
        'width', 640,
        'height', 640,
        'padValue', 114,
        'runtime', 'CUDA_ONLY'
    ),
    jsonb_build_object(
        'coordinateType', 'NORMALIZED_XYWH',
        'classes', jsonb_build_array(
            'CRACK', 'SPALLING', 'EXPOSED_REBAR', 'CORROSION',
            'WATER_SEEPAGE', 'EFFLORESCENCE', 'WALL_DAMAGE'
        )
    ),
    jsonb_build_object(
        'adapter', 'yolox-building-defect-v1',
        'providerCode', 'FAST_API',
        'capabilityType', 'VISION_INFERENCE',
        'executionProvider', 'CUDAExecutionProvider',
        'runtimeCatalogRequired', true,
        'professionalReviewRequired', true,
        'offline', true,
        'cudaRequired', true,
        'weightInstalled', false,
        'candidateConfig', 'ai-service-python/config/yolox-building-defect.candidate.json'
    ),
    'APPROVED',
    'REAL',
    'Megvii-BaseDetection/YOLOX',
    'AI-BUILDING-YOLOX-001/1.0.0',
    '6ddff4824372906469a7fae2dc3206c7aa4bbaee',
    'yolox_s_building_defect.onnx',
    NULL,
    jsonb_build_array(
        'CRACK', 'SPALLING', 'EXPOSED_REBAR', 'CORROSION',
        'WATER_SEEPAGE', 'EFFLORESCENCE', 'WALL_DAMAGE'
    ),
    jsonb_build_array(
        '业务登记已批准，但真实七类建筑病害权重尚未安装并完成独立质量验收',
        '运行时未就绪前不得设为默认推理模型',
        '真实模型结果必须经专业人员复核',
        '模型置信度不代表房屋危险概率',
        '不作为正式房屋安全鉴定结论'
    ),
    CURRENT_TIMESTAMP,
    'VALIDATING',
    FALSE,
    'VALIDATING',
    jsonb_build_object(
        'candidateOnly', true,
        'runtimeReady', false,
        'weightInstalled', false,
        'reason', 'Provider 已接入并完成代码验证，等待真实训练权重、SHA-256、CUDA 热身和独立质量验收'
    ),
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

COMMENT ON COLUMN ai.governance_setting.text_value IS
    '治理设置文本值；与 boolean_value 互斥，由约束保证每条设置只有一种值类型';
