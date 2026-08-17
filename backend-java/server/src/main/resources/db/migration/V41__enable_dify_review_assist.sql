-- 启用已经完成 DSL 和真实联调的 Dify Review Assist。
-- 仍保持 VALIDATING，禁止作为正式证据；运行时还必须同时满足：
-- 1. URBAN_SAFE_DIFY_ENABLED=true；
-- 2. review-assist 独立 API Key 配置完整。

UPDATE ai.workflow_definition
SET enabled = TRUE,
    quality_status = 'VALIDATING',
    formal_evidence_enabled = FALSE,
    current_version = 'review-assist-v1.0.0',
    input_schema_version = '1.0',
    output_schema_version = '1.0',
    updated_at = CURRENT_TIMESTAMP
WHERE workflow_code = 'DIFY-REVIEW-ASSIST-001';
