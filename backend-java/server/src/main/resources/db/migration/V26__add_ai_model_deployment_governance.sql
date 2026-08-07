-- UrbanSafe Priority Flyway migration V26
-- 将“技术包已批准”和“允许进入正式评分”拆分，避免仅完成链路验证的模型直接成为正式证据。

ALTER TABLE ai.model_registry
    ADD COLUMN deployment_stage VARCHAR(20) NOT NULL DEFAULT 'VALIDATING',
    ADD COLUMN formal_evidence_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ai.model_registry
    ADD CONSTRAINT ck_ai_model_registry_deployment_stage
        CHECK (deployment_stage IN ('VALIDATING', 'DEMO', 'SHADOW', 'ACTIVE', 'SUSPENDED'));

-- 模拟模型永远只能用于演示。
UPDATE ai.model_registry
SET deployment_stage='DEMO', formal_evidence_enabled=FALSE, updated_at=CURRENT_TIMESTAMP
WHERE mode='MOCK' AND deleted_at IS NULL;

-- 当前首个 CUDA 裂缝模型已经完成技术链路验收，但固定样例和现场盲测尚未完成。
UPDATE ai.model_registry
SET deployment_stage='DEMO', formal_evidence_enabled=FALSE, updated_at=CURRENT_TIMESTAMP
WHERE model_code='AI-CRACK-HF-UNET-001' AND deleted_at IS NULL;

COMMENT ON COLUMN ai.model_registry.deployment_stage IS
    '模型业务部署阶段：VALIDATING/DEMO/SHADOW/ACTIVE/SUSPENDED';
COMMENT ON COLUMN ai.model_registry.formal_evidence_enabled IS
    '是否允许经专业复核后的真实模型结果进入正式评分证据';
