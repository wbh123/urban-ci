-- UrbanSafe Priority Flyway migration V32
-- 内部知识问答第一版：支持同一文档编号的多版本登记，并补充有效范围检索索引。

ALTER TABLE knowledge.document
    DROP CONSTRAINT IF EXISTS document_document_code_key;

ALTER TABLE knowledge.document
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE UNIQUE INDEX IF NOT EXISTS ux_knowledge_document_code_version
    ON knowledge.document(document_code, document_version);

CREATE INDEX IF NOT EXISTS ix_knowledge_document_active_scope
    ON knowledge.document(status, community_id, building_id, effective_from, effective_to)
    WHERE status='ACTIVE';

CREATE INDEX IF NOT EXISTS ix_knowledge_document_roles
    ON knowledge.document USING GIN(role_scope);
