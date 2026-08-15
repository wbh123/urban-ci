-- 注册本地高精度视觉模型的持久化异步执行能力。
INSERT INTO ai.workflow_definition
    (workflow_code, model_code, display_name, business_scene, provider_code, capability_type,
     config_key, current_version, input_schema_version, output_schema_version, enabled,
     quality_status, formal_evidence_enabled, timeout_ms, max_attempts, data_policy)
VALUES
    ('LOCAL-VISION-ACCURACY-001', 'AI-VISION-LOCAL-001', '本地高精度病害识别',
     'DEFECT_DETECTION', 'FAST_API', 'VISION_INFERENCE', 'local-vision-accuracy',
     'ACCURACY-CANDIDATE-002', '1.0', '1.0', TRUE,
     'VALIDATING', FALSE, 180000, 2,
     '{"offline":true,"cudaRequired":true,"async":true,"profile":"ACCURACY"}'::jsonb)
ON CONFLICT (workflow_code) DO UPDATE SET
    model_code=EXCLUDED.model_code,
    display_name=EXCLUDED.display_name,
    provider_code=EXCLUDED.provider_code,
    capability_type=EXCLUDED.capability_type,
    config_key=EXCLUDED.config_key,
    current_version=EXCLUDED.current_version,
    enabled=TRUE,
    timeout_ms=EXCLUDED.timeout_ms,
    max_attempts=EXCLUDED.max_attempts,
    data_policy=EXCLUDED.data_policy,
    updated_at=CURRENT_TIMESTAMP;
