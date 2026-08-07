-- UrbanSafe Priority Flyway migration V27
-- 将“运行时可加载”和“模型质量已通过”拆分，避免 CUDA 就绪被误解为业务质量通过。

ALTER TABLE ai.model_registry
    ADD COLUMN quality_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN quality_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN quality_evaluated_at TIMESTAMPTZ;

ALTER TABLE ai.model_registry
    ADD CONSTRAINT ck_ai_model_registry_quality_status
        CHECK (quality_status IN ('UNKNOWN', 'VALIDATING', 'PASSED', 'FAILED'));

-- 模拟模型不参与真实质量验收。
UPDATE ai.model_registry
SET quality_status='UNKNOWN',
    quality_summary=jsonb_build_object('reason', '模拟模型不参与真实质量验收'),
    quality_evaluated_at=NULL,
    updated_at=CURRENT_TIMESTAMP
WHERE mode='MOCK' AND deleted_at IS NULL;

-- 首个 CUDA 裂缝模型运行链路已通过，但当前 15 张诊断样例的 NOT_APPLICABLE 比例为 100%，
-- 在确认输出激活、预处理与 ONNX 契约前保持 VALIDATING，禁止进入正式证据。
UPDATE ai.model_registry
SET deployment_stage='VALIDATING',
    formal_evidence_enabled=FALSE,
    quality_status='VALIDATING',
    quality_summary=jsonb_build_object(
        'sampleCount', 15,
        'obviousCrackRecall', 0.0,
        'notApplicableRate', 1.0,
        'reason', '需要诊断输出激活、预处理和 ONNX 契约'
    ),
    quality_evaluated_at=CURRENT_TIMESTAMP,
    updated_at=CURRENT_TIMESTAMP
WHERE model_code='AI-CRACK-HF-UNET-001' AND deleted_at IS NULL;

COMMENT ON COLUMN ai.model_registry.quality_status IS
    '模型独立质量验收状态：UNKNOWN/VALIDATING/PASSED/FAILED';
COMMENT ON COLUMN ai.model_registry.quality_summary IS
    '最近一次质量评估摘要，不包含模型权重或原始图片';
COMMENT ON COLUMN ai.model_registry.quality_evaluated_at IS
    '最近一次质量评估时间';
