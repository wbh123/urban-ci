-- UrbanSafe Priority Flyway migration V9
-- 第二阶段：地图点位、巡检状态机、现场记录与对象存储元数据。

ALTER TABLE core.inspection_task DROP CONSTRAINT IF EXISTS inspection_task_status_check;
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ;
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;
ALTER TABLE core.inspection_task ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
UPDATE core.inspection_task SET status = CASE
    WHEN status IN ('PENDING_ASSIGNMENT','PENDING_INSPECTION','DRAFT') THEN 'PENDING'
    WHEN status IN ('SUBMITTED','ANALYZING') THEN 'IN_PROGRESS'
    WHEN status = 'ANALYSIS_FAILED' THEN 'CANCELLED'
    ELSE status END;
ALTER TABLE core.inspection_task ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE core.inspection_task ADD CONSTRAINT inspection_task_status_check
    CHECK (status IN ('PENDING','IN_PROGRESS','COMPLETED','CANCELLED'));
CREATE UNIQUE INDEX IF NOT EXISTS uk_inspection_task_code_active
    ON core.inspection_task(task_code) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_inspection_task_building_status
    ON core.inspection_task(building_id,status) WHERE deleted_at IS NULL;

ALTER TABLE core.inspection_record ADD COLUMN IF NOT EXISTS issue_type VARCHAR(64) NOT NULL DEFAULT 'OTHER';
ALTER TABLE core.inspection_record ADD COLUMN IF NOT EXISTS severity VARCHAR(16) NOT NULL DEFAULT 'LOW';
ALTER TABLE core.inspection_record ADD COLUMN IF NOT EXISTS rectification_suggestion TEXT;
ALTER TABLE core.inspection_record ADD COLUMN IF NOT EXISTS extra_data JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE core.inspection_record ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE core.inspection_record DROP CONSTRAINT IF EXISTS inspection_record_severity_check;
ALTER TABLE core.inspection_record ADD CONSTRAINT inspection_record_severity_check
    CHECK (severity IN ('LOW','MEDIUM','HIGH'));
CREATE INDEX IF NOT EXISTS idx_inspection_record_task
    ON core.inspection_record(inspection_task_id,created_at DESC) WHERE deleted_at IS NULL;

ALTER TABLE asset.file_asset ADD COLUMN IF NOT EXISTS storage_provider VARCHAR(32) NOT NULL DEFAULT 'MINIO';
ALTER TABLE asset.file_asset ADD COLUMN IF NOT EXISTS object_etag VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS uk_file_asset_object_active
    ON asset.file_asset(bucket_name,object_key) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_asset_binding_business
    ON asset.asset_binding(business_type,business_id) WHERE deleted_at IS NULL;

ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS formatted_address VARCHAR(512);
ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS source_adcode VARCHAR(32);
ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS source_citycode VARCHAR(32);
ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS match_level VARCHAR(64);
ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE geo.community_location ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE UNIQUE INDEX IF NOT EXISTS uk_community_location_active
    ON geo.community_location(community_id) WHERE deleted_at IS NULL;
