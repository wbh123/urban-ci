-- UrbanSafe Priority Flyway migration V3
-- 文件、巡检任务、巡检记录和楼栋证据。

CREATE TABLE asset.file_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_name VARCHAR(128) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size >= 0),
    sha256 CHAR(64) NOT NULL,
    business_type VARCHAR(64),
    business_id UUID,
    upload_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (upload_status IN ('UPLOADING', 'AVAILABLE', 'FAILED', 'ORPHANED', 'ARCHIVED')),
    uploaded_by UUID REFERENCES core.user_account(id),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE core.inspection_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_code VARCHAR(64) NOT NULL,
    building_id UUID NOT NULL REFERENCES core.building(id),
    inspection_type VARCHAR(64) NOT NULL,
    planned_at TIMESTAMPTZ,
    assigned_to UUID REFERENCES core.user_account(id),
    focus_parts JSONB NOT NULL DEFAULT '[]'::jsonb,
    duplicate_reason TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ASSIGNMENT'
        CHECK (status IN (
            'PENDING_ASSIGNMENT',
            'PENDING_INSPECTION',
            'DRAFT',
            'SUBMITTED',
            'ANALYZING',
            'COMPLETED',
            'ANALYSIS_FAILED',
            'CANCELLED'
        )),
    created_by UUID REFERENCES core.user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    remark TEXT
);

CREATE TABLE core.inspection_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inspection_task_id UUID REFERENCES core.inspection_task(id),
    building_id UUID NOT NULL REFERENCES core.building(id),
    inspector_id UUID REFERENCES core.user_account(id),
    inspection_part VARCHAR(128),
    inspected_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN (
            'DRAFT',
            'SUBMITTED',
            'ANALYZING',
            'COMPLETED',
            'ANALYSIS_FAILED',
            'CANCELLED'
        )),
    summary TEXT,
    form_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,
    remark TEXT
);

CREATE TABLE core.building_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    evidence_type VARCHAR(64) NOT NULL
        CHECK (evidence_type IN (
            'MAINTENANCE_RECORD',
            'PROFESSIONAL_INSPECTION',
            'HISTORICAL_COMPLAINT',
            'PUBLIC_VALUE',
            'ENVIRONMENT_RISK',
            'OTHER'
        )),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMPTZ,
    source VARCHAR(255),
    reliability_level VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    evidence_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES core.user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE asset.asset_binding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id UUID NOT NULL REFERENCES asset.file_asset(id),
    business_type VARCHAR(64) NOT NULL,
    business_id UUID NOT NULL,
    binding_role VARCHAR(64) NOT NULL DEFAULT 'ATTACHMENT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE TABLE core.resident_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_code VARCHAR(64) NOT NULL,
    community_id UUID NOT NULL REFERENCES core.community(id),
    building_id UUID REFERENCES core.building(id),
    reporter_user_id UUID REFERENCES core.user_account(id),
    report_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    urgency VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

COMMENT ON COLUMN asset.file_asset.deleted_at IS '逻辑删除时间；对象本体由受控清理任务处理';
COMMENT ON COLUMN core.inspection_task.deleted_at IS '逻辑删除时间，历史任务不执行物理删除';
COMMENT ON COLUMN core.inspection_record.deleted_at IS '逻辑删除时间，已提交记录原则上仅归档';
