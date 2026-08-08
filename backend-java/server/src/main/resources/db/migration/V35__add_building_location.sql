-- UrbanSafe Priority Flyway migration V35
-- 为楼栋增加独立中心点定位记录；位置点与楼栋 Polygon/MultiPolygon 空间边界保持分离。

CREATE TABLE geo.building_location (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id UUID NOT NULL REFERENCES core.building(id),
    centroid geometry(Point, 4326) NOT NULL,
    formatted_address TEXT,
    source_provider VARCHAR(32) NOT NULL
        CHECK (source_provider IN ('AMAP', 'MANUAL', 'IMPORT', 'MOCK')),
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

CREATE UNIQUE INDEX uk_building_location_active
    ON geo.building_location (building_id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_building_location_centroid_gist
    ON geo.building_location USING GIST (centroid)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE geo.building_location IS
    '楼栋地图中心位置；与楼栋真实空间边界分开保存，可来源于高德、人工确认或结构化导入';
COMMENT ON COLUMN geo.building_location.source_coordinate_system IS
    '真实来源坐标体系；高德坐标必须明确记录为 GCJ02，不得误标为 EPSG:4326/WGS84';
COMMENT ON COLUMN geo.building_location.provider_response_hash IS
    '外部服务响应摘要，用于追溯和重复检测，不保存访问密钥';
