-- 扩展公众反馈闭环，兼容已有 resident_report 演示数据。
ALTER TABLE core.resident_report
    ADD COLUMN IF NOT EXISTS reporter_name VARCHAR(128),
    ADD COLUMN IF NOT EXISTS contact_phone VARCHAR(32),
    ADD COLUMN IF NOT EXISTS contact_email VARCHAR(255),
    ADD COLUMN IF NOT EXISTS contact_consent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS feedback_channel VARCHAR(32) NOT NULL DEFAULT 'WEB',
    ADD COLUMN IF NOT EXISTS location_text VARCHAR(512),
    ADD COLUMN IF NOT EXISTS recorded_by UUID REFERENCES core.user_account(id),
    ADD COLUMN IF NOT EXISTS handling_summary TEXT,
    ADD COLUMN IF NOT EXISTS tracking_secret_hash CHAR(64),
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_by UUID REFERENCES core.user_account(id);

ALTER TABLE core.resident_report
    ADD CONSTRAINT ck_resident_report_status
    CHECK (status IN (
        'SUBMITTED', 'ACCEPTED', 'PROCESSING', 'NEED_MORE_INFO',
        'RESOLVED', 'CLOSED', 'REJECTED', 'CANCELLED'
    ));

ALTER TABLE core.resident_report
    ADD CONSTRAINT ck_resident_report_urgency
    CHECK (urgency IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

ALTER TABLE core.resident_report
    ADD CONSTRAINT ck_resident_report_channel
    CHECK (feedback_channel IN ('WEB', 'PHONE', 'SMS', 'COUNTER', 'INTERNAL'));

CREATE TABLE core.resident_report_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resident_report_id UUID NOT NULL REFERENCES core.resident_report(id),
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    message TEXT,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    actor_type VARCHAR(32) NOT NULL DEFAULT 'SYSTEM'
        CHECK (actor_type IN ('CITIZEN', 'STAFF', 'SYSTEM')),
    actor_user_id UUID REFERENCES core.user_account(id),
    event_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resident_report_status_time
    ON core.resident_report (status, submitted_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_resident_report_channel_time
    ON core.resident_report (feedback_channel, submitted_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_resident_report_tracking_hash
    ON core.resident_report (tracking_secret_hash)
    WHERE deleted_at IS NULL AND tracking_secret_hash IS NOT NULL;

CREATE INDEX idx_resident_report_event_report_time
    ON core.resident_report_event (resident_report_id, created_at);

COMMENT ON COLUMN core.resident_report.tracking_secret_hash IS
    '匿名进度查询凭证的 SHA-256 摘要；原始凭证只在创建响应中返回一次';
COMMENT ON TABLE core.resident_report_event IS
    '公众反馈不可覆盖的处理时间线；PUBLIC 事件可向反馈人展示，INTERNAL 仅内部可见';
