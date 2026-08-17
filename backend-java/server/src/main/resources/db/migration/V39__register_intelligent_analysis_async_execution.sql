-- Spring AI 综合研判异步执行工作流。
-- 复用 ai.execution_task 的 PostgreSQL 持久化队列、租约、重试与恢复机制。

INSERT INTO ai.workflow_definition
    (workflow_code, model_code, display_name, business_scene, provider_code, capability_type,
     config_key, current_version, input_schema_version, output_schema_version, enabled,
     quality_status, formal_evidence_enabled, timeout_ms, max_attempts, data_policy)
VALUES
    ('SPRING-AI-INTELLIGENT-ANALYSIS-001', NULL, 'Spring AI 智能综合研判', 'INTELLIGENT_ANALYSIS',
     'SPRING_AI', 'TEXT_GENERATION', 'spring-ai-intelligent-analysis', '1.0.0', '1.0', '1.0', TRUE,
     'VALIDATING', FALSE, 300000, 2,
     '{"async":true,"writeBusinessData":false,"requiresAuthorizedContext":true}'::jsonb)
ON CONFLICT (workflow_code) DO UPDATE SET
    display_name=EXCLUDED.display_name,
    business_scene=EXCLUDED.business_scene,
    provider_code=EXCLUDED.provider_code,
    capability_type=EXCLUDED.capability_type,
    config_key=EXCLUDED.config_key,
    current_version=EXCLUDED.current_version,
    input_schema_version=EXCLUDED.input_schema_version,
    output_schema_version=EXCLUDED.output_schema_version,
    enabled=TRUE,
    timeout_ms=EXCLUDED.timeout_ms,
    max_attempts=EXCLUDED.max_attempts,
    data_policy=EXCLUDED.data_policy,
    updated_at=CURRENT_TIMESTAMP;
