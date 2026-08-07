-- UrbanSafe Priority Flyway migration V25
-- 第六阶段：登记已经通过本地 CUDA-only 运行时验证的裂缝分割真实模型。
--
-- 说明：
-- 1. 本迁移只保存业务侧可查询的模型准入元数据；
-- 2. ONNX 权重、runtime-catalog.json、manifest.json 和数据集文件仍保存在本地模型目录或对象存储；
-- 3. 不在数据库中保存绝对路径、密钥、令牌、样本图片或训练数据。

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
    approved_at
)
VALUES (
    'AI-CRACK-HF-UNET-001',
    'Hugging Face U-Net 裂缝分割模型',
    '1.0.0',
    'CRACK_SEGMENTATION',
    'ONNX Runtime CUDAExecutionProvider',
    'OPEN_MODEL_PACKAGE',
    'MIT',
    jsonb_build_object(
        'contentTypes', jsonb_build_array('image/jpeg', 'image/png', 'image/webp'),
        'imageSize', 640,
        'tensorLayout', 'NCHW',
        'runtime', 'CUDA_ONLY'
    ),
    jsonb_build_object(
        'coordinateType', 'NORMALIZED_XYWH',
        'classes', jsonb_build_array('CRACK'),
        'maskThreshold', 0.50
    ),
    jsonb_build_object(
        'adapter', 'onnx-crack-segmentation-v1',
        'executionProvider', 'CUDAExecutionProvider',
        'runtimeCatalogRequired', true,
        'professionalReviewRequired', true
    ),
    'APPROVED',
    'REAL',
    'Hugging Face',
    'local-model-package/AI-CRACK-HF-UNET-001',
    '1.0.0',
    'model.onnx',
    '4deff4d3a21e8b01e547c57b07398a0d9f9794534a61c978722390ef1f49a4a2',
    jsonb_build_array('CRACK'),
    jsonb_build_array(
        '模型输出仅用于裂缝风险筛查辅助，不作为正式房屋安全鉴定结论',
        '置信度不等于房屋危险概率',
        '真实结果必须经专业人员确认或修正后才能进入正式评分证据',
        '低光、污渍、施工缝、边缘和剥落等困难负样本仍需持续扩充独立评估'
    ),
    TIMESTAMPTZ '2026-07-28 12:00:00+08'
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
    updated_at = CURRENT_TIMESTAMP;
