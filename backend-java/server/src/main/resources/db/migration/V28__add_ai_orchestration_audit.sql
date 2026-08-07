-- UrbanSafe Priority Flyway migration V28
-- 第七阶段第一轮：记录混合人工智能提供者、能力、工作流和统一结构化结果。

ALTER TABLE ai.inference_task
    ADD COLUMN provider_code VARCHAR(64) NOT NULL DEFAULT 'FAST_API',
    ADD COLUMN capability_type VARCHAR(32) NOT NULL DEFAULT 'VISION_INFERENCE',
    ADD COLUMN workflow_code VARCHAR(128),
    ADD COLUMN workflow_version VARCHAR(64),
    ADD COLUMN fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN fallback_provider_code VARCHAR(64),
    ADD COLUMN fallback_reason VARCHAR(512);

ALTER TABLE ai.inference_task
    ADD CONSTRAINT ck_ai_inference_task_capability_type
        CHECK (capability_type IN ('VISION_INFERENCE', 'WORKFLOW', 'TEXT_GENERATION')),
    ADD CONSTRAINT ck_ai_inference_task_fallback_audit
        CHECK (fallback_used OR (fallback_provider_code IS NULL AND fallback_reason IS NULL));

ALTER TABLE ai.inference_result
    ADD COLUMN structured_result JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN raw_response_reference VARCHAR(256);

CREATE INDEX idx_ai_inference_task_provider
    ON ai.inference_task (provider_code, capability_type, created_at DESC);

-- 在线能力登记表示“适配器契约允许接入”，不代表模型质量通过或可进入正式评分。
INSERT INTO ai.model_registry (
    model_code, model_name, model_version, model_type, framework, source_type,
    license_name, input_spec, output_spec, model_config, status, mode,
    source_platform, supported_classes, limitations, approved_at,
    deployment_stage, formal_evidence_enabled, quality_status, quality_summary
) VALUES
(
    'AI-DIFY-WORKFLOW-001',
    'UrbanSafe Dify Image Analysis Workflow',
    'image-analysis-v1.0.0',
    'MULTIMODAL_WORKFLOW',
    'Dify Workflow',
    'ONLINE_API',
    'EXTERNAL-SERVICE-TERMS',
    '{"assetRequired":true,"dataMinimization":true}'::jsonb,
    '{"contract":"AiStructuredResult","confidenceRange":[0,1]}'::jsonb,
    '{"providerCode":"DIFY","credentialsExternalized":true}'::jsonb,
    'APPROVED',
    'REAL',
    'DIFY',
    '["crack","spalling","seepage","corrosion","exposed_rebar"]'::jsonb,
    '["仅用于辅助筛查","不得覆盖规则评分","必须人工复核","Cloud阶段需执行隐私最小化"]'::jsonb,
    CURRENT_TIMESTAMP,
    'VALIDATING',
    FALSE,
    'VALIDATING',
    '{"reason":"等待真实工作流、固定样例和隐私评审"}'::jsonb
),
(
    'AI-SPRING-AI-DIRECT-001',
    'UrbanSafe Spring AI Direct Vision Provider',
    '1.0.0',
    'MULTIMODAL_GENERATION',
    'Spring AI',
    'ONLINE_API',
    'EXTERNAL-SERVICE-TERMS',
    '{"assetRequired":true,"dataMinimization":true}'::jsonb,
    '{"contract":"AiStructuredResult","confidenceRange":[0,1]}'::jsonb,
    '{"providerCode":"SPRING_AI","credentialsExternalized":true}'::jsonb,
    'APPROVED',
    'REAL',
    'SPRING_AI',
    '[]'::jsonb,
    '["仅用于辅助筛查","不得覆盖规则评分","必须人工复核","供应商模型通过配置选择"]'::jsonb,
    CURRENT_TIMESTAMP,
    'VALIDATING',
    FALSE,
    'VALIDATING',
    '{"reason":"等待在线模型固定样例、成本、时延和一致性评估"}'::jsonb
)
ON CONFLICT DO NOTHING;

COMMENT ON COLUMN ai.inference_task.provider_code IS '实际选择的人工智能提供者稳定编号';
COMMENT ON COLUMN ai.inference_task.capability_type IS '人工智能能力类型';
COMMENT ON COLUMN ai.inference_task.workflow_code IS '工作流编号，仅用于追溯，不保存密钥';
COMMENT ON COLUMN ai.inference_task.fallback_used IS '是否执行了配置明确允许的降级；第一轮固定为 false';
COMMENT ON COLUMN ai.inference_result.structured_result IS '通过后端校验的统一结构化结果';
COMMENT ON COLUMN ai.inference_result.raw_response_reference IS '受控原始响应引用或摘要，不保存完整供应商响应';
