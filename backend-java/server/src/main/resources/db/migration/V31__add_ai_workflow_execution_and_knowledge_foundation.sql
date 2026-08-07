-- UrbanSafe Priority Flyway migration V31
-- 第七阶段第二轮：持久化人工智能执行、多工作流登记和内部知识检索基础。

CREATE SCHEMA IF NOT EXISTS knowledge;

CREATE TABLE ai.workflow_definition (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_code VARCHAR(96) NOT NULL UNIQUE,
    model_code VARCHAR(96),
    display_name VARCHAR(160) NOT NULL,
    business_scene VARCHAR(64) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    capability_type VARCHAR(32) NOT NULL,
    config_key VARCHAR(64) NOT NULL,
    current_version VARCHAR(64) NOT NULL,
    input_schema_version VARCHAR(32) NOT NULL,
    output_schema_version VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    quality_status VARCHAR(32) NOT NULL DEFAULT 'VALIDATING',
    formal_evidence_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    timeout_ms INTEGER NOT NULL DEFAULT 300000 CHECK (timeout_ms BETWEEN 1000 AND 900000),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts BETWEEN 1 AND 10),
    data_policy JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_provider CHECK (provider_code IN ('DIFY','FAST_API','SPRING_AI')),
    CONSTRAINT ck_workflow_capability CHECK (capability_type IN ('WORKFLOW','VISION_INFERENCE','TEXT_GENERATION')),
    CONSTRAINT ck_workflow_quality CHECK (quality_status IN ('PLANNED','VALIDATING','APPROVED','REJECTED','DEFERRED')),
    CONSTRAINT ck_workflow_formal_evidence CHECK (NOT formal_evidence_enabled OR quality_status='APPROVED')
);

CREATE UNIQUE INDEX ux_workflow_definition_model_code
    ON ai.workflow_definition(model_code)
    WHERE model_code IS NOT NULL;

CREATE TABLE ai.execution_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_code VARCHAR(96) NOT NULL REFERENCES ai.workflow_definition(workflow_code),
    asset_id UUID,
    mode VARCHAR(16) NOT NULL,
    model_id VARCHAR(96) NOT NULL,
    provider_code VARCHAR(32) NOT NULL,
    capability_type VARCHAR(32) NOT NULL,
    prompt TEXT,
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    requested_by UUID,
    inputs JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    inference_id UUID,
    error_code VARCHAR(96),
    error_message VARCHAR(1000),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_execution_mode CHECK (mode IN ('MOCK','REAL')),
    CONSTRAINT ck_execution_status CHECK (status IN ('PENDING','READY','RUNNING','SUCCEEDED','FAILED','RETRY_WAIT','CANCELLED','REJECTED')),
    CONSTRAINT ck_execution_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_execution_lease CHECK ((status='RUNNING') = (lease_owner IS NOT NULL AND lease_until IS NOT NULL))
);

CREATE INDEX ix_execution_task_claim
    ON ai.execution_task(status, available_at, created_at)
    WHERE status IN ('PENDING','READY','RETRY_WAIT');
CREATE INDEX ix_execution_task_lease
    ON ai.execution_task(lease_until)
    WHERE status='RUNNING';
CREATE INDEX ix_execution_task_asset ON ai.execution_task(asset_id, created_at DESC);

CREATE TABLE knowledge.document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_code VARCHAR(96) NOT NULL UNIQUE,
    title VARCHAR(240) NOT NULL,
    document_type VARCHAR(64) NOT NULL,
    document_version VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    security_level VARCHAR(24) NOT NULL DEFAULT 'INTERNAL',
    role_scope TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    community_id UUID,
    building_id UUID,
    source_asset_id UUID,
    source_uri TEXT,
    content_checksum VARCHAR(128) NOT NULL,
    effective_from TIMESTAMPTZ,
    effective_to TIMESTAMPTZ,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_knowledge_document_status CHECK (status IN ('DRAFT','ACTIVE','EXPIRED','ARCHIVED')),
    CONSTRAINT ck_knowledge_security CHECK (security_level IN ('PUBLIC','INTERNAL','RESTRICTED')),
    CONSTRAINT ck_knowledge_effective_range CHECK (effective_to IS NULL OR effective_from IS NULL OR effective_to > effective_from)
);

CREATE TABLE knowledge.chunk (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES knowledge.document(id),
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    section_title VARCHAR(300),
    page_number INTEGER CHECK (page_number IS NULL OR page_number > 0),
    content TEXT NOT NULL,
    search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', coalesce(section_title,'') || ' ' || content)) STORED,
    embedding VECTOR(1536),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id, chunk_index)
);

CREATE INDEX ix_knowledge_chunk_search ON knowledge.chunk USING GIN(search_vector);
CREATE INDEX ix_knowledge_chunk_document ON knowledge.chunk(document_id, chunk_index);

CREATE TABLE knowledge.question (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_text TEXT NOT NULL,
    answer_text TEXT,
    evidence_sufficient BOOLEAN NOT NULL DEFAULT FALSE,
    workflow_code VARCHAR(96),
    workflow_version VARCHAR(64),
    model_code VARCHAR(96),
    provider_code VARCHAR(32),
    requested_by UUID NOT NULL,
    request_context JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(96),
    error_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMPTZ,
    CONSTRAINT ck_knowledge_question_status CHECK (status IN ('PENDING','ANSWERED','REFUSED','FAILED'))
);

CREATE TABLE knowledge.citation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id UUID NOT NULL REFERENCES knowledge.question(id),
    chunk_id UUID NOT NULL REFERENCES knowledge.chunk(id),
    citation_order INTEGER NOT NULL CHECK (citation_order > 0),
    relevance_score NUMERIC(8,6),
    quoted_text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(question_id, citation_order),
    UNIQUE(question_id, chunk_id)
);

INSERT INTO ai.workflow_definition
    (workflow_code, model_code, display_name, business_scene, provider_code, capability_type,
     config_key, current_version, input_schema_version, output_schema_version, enabled,
     quality_status, formal_evidence_enabled, timeout_ms, max_attempts, data_policy)
VALUES
    ('DIFY-IMAGE-ANALYSIS-001', 'AI-DIFY-WORKFLOW-001', '建筑表观病害分析', 'IMAGE_ANALYSIS',
     'DIFY', 'WORKFLOW', 'image-analysis', 'image-analysis-v1.1.0', '1.1', '1.1', TRUE,
     'VALIDATING', FALSE, 300000, 3, '{"sendPersonalData":false,"requiresImage":true}'::jsonb),
    ('DIFY-REVIEW-ASSIST-001', NULL, '巡检复核辅助', 'REVIEW_ASSIST',
     'DIFY', 'WORKFLOW', 'review-assist', 'review-assist-v1.0.0', '1.0', '1.0', FALSE,
     'PLANNED', FALSE, 180000, 2, '{"writeBusinessData":false}'::jsonb),
    ('DIFY-REPORT-DRAFT-001', NULL, '楼栋报告草稿', 'REPORT_DRAFT',
     'DIFY', 'WORKFLOW', 'report-draft', 'report-draft-v1.0.0', '1.0', '1.0', FALSE,
     'PLANNED', FALSE, 180000, 2, '{"publishAllowed":false}'::jsonb),
    ('DIFY-KNOWLEDGE-QA-001', NULL, '城安内部知识问答', 'KNOWLEDGE_QA',
     'DIFY', 'TEXT_GENERATION', 'knowledge-qa', 'knowledge-qa-v1.0.0', '1.0', '1.0', FALSE,
     'PLANNED', FALSE, 120000, 2, '{"requiresAuthorizedContext":true}'::jsonb),
    ('LOCAL-IMAGE-QUALITY-001', NULL, '本地图片质量与适用性检测', 'IMAGE_QUALITY',
     'FAST_API', 'VISION_INFERENCE', 'local-image-quality', '0.1.0', '1.0', '1.0', FALSE,
     'VALIDATING', FALSE, 15000, 1, '{"offline":true,"cpuAllowed":true}'::jsonb),
    ('LOCAL-DEFECT-SEGMENTATION-001', NULL, '本地病害精确分割', 'DEFECT_SEGMENTATION',
     'FAST_API', 'VISION_INFERENCE', 'local-defect-segmentation', 'planned', '1.0', '1.0', FALSE,
     'PLANNED', FALSE, 60000, 1, '{"offline":true,"cudaRequired":true}'::jsonb)
ON CONFLICT (workflow_code) DO NOTHING;

COMMENT ON TABLE ai.workflow_definition IS '人工智能能力和 Dify 应用登记，不保存 API Key';
COMMENT ON TABLE ai.execution_task IS '可恢复、可租约领取的人工智能异步执行任务';
COMMENT ON TABLE knowledge.document IS '受权限控制的知识文档元数据';
COMMENT ON TABLE knowledge.chunk IS '知识文档切块、全文检索和可选向量';
COMMENT ON TABLE knowledge.question IS '内部知识问答请求和受控答案';
COMMENT ON TABLE knowledge.citation IS '问答使用的文档片段引用';
