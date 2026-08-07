-- UrbanSafe Priority Flyway migration V7
-- 第一阶段业务约束，以及为后续地图可视化和真实地址地理编码预留的小区位置表。

ALTER TABLE core.community
    ADD CONSTRAINT ck_community_status
    CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE core.building
    ADD CONSTRAINT ck_building_status
    CHECK (status IN ('ACTIVE', 'INACTIVE'));

ALTER TABLE core.building_evidence
    ADD CONSTRAINT ck_building_evidence_reliability
    CHECK (reliability_level IN (
        'UNVERIFIED',
        'USER_PROVIDED',
        'OFFICIAL_RECORD',
        'PROFESSIONAL_CONFIRMED'
    ));

-- 普通住宅小区通过地址地理编码通常只能得到中心位置点，不能保证得到真实边界。
-- 因此位置点与 community_boundary 分开保存，禁止使用虚构多边形代替真实边界。
CREATE TABLE geo.community_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    community_id UUID NOT NULL REFERENCES core.community(id),
    centroid geometry(Point, 4326) NOT NULL,
    formatted_address TEXT,
    source_provider VARCHAR(32) NOT NULL
        CHECK (source_provider IN ('AMAP', 'MANUAL', 'IMPORT')),
    source_coordinate_system VARCHAR(32) NOT NULL
        CHECK (source_coordinate_system IN ('GCJ02', 'WGS84', 'BD09', 'UNKNOWN')),
    source_adcode VARCHAR(16),
    source_citycode VARCHAR(16),
    match_level VARCHAR(64),
    quality_score NUMERIC(5, 2)
        CHECK (quality_score IS NULL OR quality_score BETWEEN 0 AND 100),
    provider_response_hash CHAR(64),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    collected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_community_location_active
    ON geo.community_location (community_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_community_location_centroid_gist
    ON geo.community_location USING GIST (centroid)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_community_location_adcode_active
    ON geo.community_location (source_adcode)
    WHERE deleted_at IS NULL AND source_adcode IS NOT NULL;

COMMENT ON TABLE geo.community_location IS
    '小区地图中心位置；与真实小区边界分开保存，可来源于高德地理编码、人工确认或结构化导入';
COMMENT ON COLUMN geo.community_location.source_coordinate_system IS
    '真实来源坐标体系；高德坐标必须明确记录为 GCJ02，不得误标为 EPSG:4326/WGS84';
COMMENT ON COLUMN geo.community_location.provider_response_hash IS
    '外部服务响应摘要，用于追溯和重复检测，不保存访问密钥';
