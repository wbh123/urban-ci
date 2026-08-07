-- UrbanSafe Priority Flyway migration V21
-- 第五阶段：报告版本、源数据快照、幂等生成和查询索引。

ALTER TABLE asset.generated_report
    ADD COLUMN IF NOT EXISTS template_version VARCHAR(64),
    ADD COLUMN IF NOT EXISTS report_format VARCHAR(16),
    ADD COLUMN IF NOT EXISTS source_checksum CHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(160),
    ADD COLUMN IF NOT EXISTS report_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS generated_by UUID REFERENCES core.user_account(id),
    ADD COLUMN IF NOT EXISTS generation_duration_ms BIGINT,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(64),
    ADD COLUMN IF NOT EXISTS error_message TEXT;

UPDATE asset.generated_report
SET template_version = COALESCE(template_version, 'phase5-report-v1'),
    report_format = COALESCE(report_format, 'PDF'),
    source_checksum = COALESCE(source_checksum, repeat('0', 64)),
    idempotency_key = COALESCE(idempotency_key, report_code)
WHERE template_version IS NULL
   OR report_format IS NULL
   OR source_checksum IS NULL
   OR idempotency_key IS NULL;

ALTER TABLE asset.generated_report
    ALTER COLUMN template_version SET NOT NULL,
    ALTER COLUMN report_format SET NOT NULL,
    ALTER COLUMN report_format SET DEFAULT 'PDF',
    ALTER COLUMN source_checksum SET NOT NULL,
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE asset.generated_report
    DROP CONSTRAINT IF EXISTS generated_report_format_check;
ALTER TABLE asset.generated_report
    ADD CONSTRAINT generated_report_format_check
    CHECK (report_format IN ('PDF'));

ALTER TABLE asset.generated_report
    DROP CONSTRAINT IF EXISTS generated_report_duration_check;
ALTER TABLE asset.generated_report
    ADD CONSTRAINT generated_report_duration_check
    CHECK (generation_duration_ms IS NULL OR generation_duration_ms >= 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_generated_report_idempotency_active
    ON asset.generated_report(idempotency_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_generated_report_building_created
    ON asset.generated_report(building_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_generated_report_status_created
    ON asset.generated_report(report_status, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_generated_report_community_created
    ON asset.generated_report(community_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_generated_report_source_checksum
    ON asset.generated_report(building_id, template_version, source_checksum)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN asset.generated_report.source_checksum IS
    '规范化报告源数据快照的 SHA-256，用于复现、过期判断和幂等复用';
COMMENT ON COLUMN asset.generated_report.report_snapshot IS
    '生成文件时使用的不可变结构化快照，不保存公众查询凭证、明文联系方式或临时访问地址';
COMMENT ON COLUMN asset.generated_report.idempotency_key IS
    '正常生成由楼栋、模板版本和源摘要组成；强制生成附加随机后缀';
