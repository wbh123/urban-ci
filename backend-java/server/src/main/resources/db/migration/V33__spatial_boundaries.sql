-- UrbanSafe Priority Flyway migration V33
-- R2：将早期单版本空间表增量升级为可确认、可版本化、可审计的边界模型。
--
-- 关键兼容策略：
-- 1. 不删除 V4 已有 community_boundary 数据，只原位扩展；
-- 2. building_geometry 重命名为 building_boundary，但在迁移末尾建立同名只读兼容视图；
-- 3. GCJ-02 等展示坐标只记录在 display_coordinate_system，display_geometry 不伪装成 EPSG:4326；
-- 4. 旧 WGS84 数据回填为 UNVERIFIED，必须经过后续人工确认后才进入正式 Polygon 查询。

-- -----------------------------------------------------------------------------
-- 1. 小区边界：扩展 V4 旧表
-- -----------------------------------------------------------------------------
ALTER TABLE geo.community_boundary
    ALTER COLUMN boundary DROP NOT NULL,
    ALTER COLUMN coordinate_reference_system DROP NOT NULL,
    ALTER COLUMN source DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(32),
    ADD COLUMN source_provider VARCHAR(32),
    ADD COLUMN source_object_id VARCHAR(255),
    ADD COLUMN source_coordinate_system VARCHAR(32),
    ADD COLUMN source_geometry_json JSONB,
    ADD COLUMN display_coordinate_system VARCHAR(32),
    ADD COLUMN display_geometry geometry(MultiPolygon),
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN verified_by UUID REFERENCES core.user_account(id),
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN remark TEXT;

UPDATE geo.community_boundary
SET source_type = COALESCE(source_type, 'GEOJSON_IMPORT'),
    source_provider = COALESCE(source_provider, NULLIF(source, ''), 'INTERNAL'),
    source_coordinate_system = COALESCE(
        source_coordinate_system,
        CASE
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) IN ('EPSG:4326', 'WGS84')
                THEN 'WGS84'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'GCJ02'
                THEN 'GCJ02'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'BD09'
                THEN 'BD09'
            ELSE 'UNKNOWN'
        END),
    source_geometry_json = COALESCE(
        source_geometry_json,
        CASE WHEN boundary IS NULL THEN NULL ELSE ST_AsGeoJSON(boundary)::jsonb END),
    display_coordinate_system = COALESCE(
        display_coordinate_system,
        CASE
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) IN ('EPSG:4326', 'WGS84')
                THEN 'WGS84'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'GCJ02'
                THEN 'GCJ02'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'BD09'
                THEN 'BD09'
            ELSE 'UNKNOWN'
        END),
    display_geometry = COALESCE(
        display_geometry,
        CASE WHEN boundary IS NULL THEN NULL ELSE ST_SetSRID(boundary, 0) END),
    status = COALESCE(status, 'UNVERIFIED'),
    version = COALESCE(version, 0);

ALTER TABLE geo.community_boundary
    ADD CONSTRAINT ck_community_boundary_source_type
        CHECK (source_type IS NULL OR source_type IN (
            'AMAP_AOI', 'MANUAL_EDIT', 'MANUAL_DRAW', 'GEOJSON_IMPORT'
        )),
    ADD CONSTRAINT ck_community_boundary_source_coordinate_system
        CHECK (source_coordinate_system IS NULL OR source_coordinate_system IN (
            'GCJ02', 'WGS84', 'BD09', 'UNKNOWN'
        )),
    ADD CONSTRAINT ck_community_boundary_display_coordinate_system
        CHECK (display_coordinate_system IS NULL OR display_coordinate_system IN (
            'GCJ02', 'WGS84', 'BD09', 'UNKNOWN'
        )),
    ADD CONSTRAINT ck_community_boundary_status
        CHECK (status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    ADD CONSTRAINT ck_community_boundary_version
        CHECK (version >= 0),
    ADD CONSTRAINT ck_community_boundary_geometry_kind
        CHECK (display_geometry IS NULL OR GeometryType(display_geometry) = 'MULTIPOLYGON');

CREATE INDEX idx_community_boundary_display_gist
    ON geo.community_boundary USING GIST (display_geometry)
    WHERE deleted_at IS NULL AND display_geometry IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 2. 楼栋边界：将 V4 building_geometry 平滑演进为 building_boundary
-- -----------------------------------------------------------------------------
ALTER TABLE geo.building_geometry RENAME TO building_boundary;
ALTER TABLE geo.building_boundary
    RENAME CONSTRAINT building_geometry_pkey TO building_boundary_pkey;
ALTER INDEX IF EXISTS geo.uk_building_geometry_active
    RENAME TO uk_building_boundary_active;
ALTER INDEX IF EXISTS geo.idx_building_geometry_footprint_gist
    RENAME TO idx_building_boundary_footprint_gist;
ALTER INDEX IF EXISTS geo.idx_building_geometry_centroid_gist
    RENAME TO idx_building_boundary_centroid_gist;

ALTER TABLE geo.building_boundary
    ALTER COLUMN centroid DROP NOT NULL,
    ALTER COLUMN coordinate_reference_system DROP NOT NULL,
    ALTER COLUMN coordinate_source DROP NOT NULL,
    ADD COLUMN source_type VARCHAR(32),
    ADD COLUMN source_provider VARCHAR(32),
    ADD COLUMN source_object_id VARCHAR(255),
    ADD COLUMN source_coordinate_system VARCHAR(32),
    ADD COLUMN source_geometry_json JSONB,
    ADD COLUMN display_coordinate_system VARCHAR(32),
    ADD COLUMN display_geometry geometry(MultiPolygon),
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN verified_by UUID REFERENCES core.user_account(id),
    ADD COLUMN verified_at TIMESTAMPTZ,
    ADD COLUMN remark TEXT;

UPDATE geo.building_boundary
SET source_type = COALESCE(source_type, 'GEOJSON_IMPORT'),
    source_provider = COALESCE(source_provider, NULLIF(coordinate_source, ''), 'INTERNAL'),
    source_coordinate_system = COALESCE(
        source_coordinate_system,
        CASE
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) IN ('EPSG:4326', 'WGS84')
                THEN 'WGS84'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'GCJ02'
                THEN 'GCJ02'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'BD09'
                THEN 'BD09'
            ELSE 'UNKNOWN'
        END),
    source_geometry_json = COALESCE(
        source_geometry_json,
        CASE WHEN footprint IS NULL THEN NULL ELSE ST_AsGeoJSON(footprint)::jsonb END),
    display_coordinate_system = COALESCE(
        display_coordinate_system,
        CASE
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) IN ('EPSG:4326', 'WGS84')
                THEN 'WGS84'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'GCJ02'
                THEN 'GCJ02'
            WHEN UPPER(COALESCE(coordinate_reference_system, '')) = 'BD09'
                THEN 'BD09'
            ELSE 'UNKNOWN'
        END),
    display_geometry = COALESCE(
        display_geometry,
        CASE WHEN footprint IS NULL THEN NULL ELSE ST_SetSRID(footprint, 0) END),
    status = COALESCE(status, 'UNVERIFIED'),
    version = COALESCE(version, 0);

ALTER TABLE geo.building_boundary
    ADD CONSTRAINT ck_building_boundary_source_type
        CHECK (source_type IS NULL OR source_type IN (
            'AMAP_AOI', 'MANUAL_EDIT', 'MANUAL_DRAW', 'GEOJSON_IMPORT'
        )),
    ADD CONSTRAINT ck_building_boundary_source_coordinate_system
        CHECK (source_coordinate_system IS NULL OR source_coordinate_system IN (
            'GCJ02', 'WGS84', 'BD09', 'UNKNOWN'
        )),
    ADD CONSTRAINT ck_building_boundary_display_coordinate_system
        CHECK (display_coordinate_system IS NULL OR display_coordinate_system IN (
            'GCJ02', 'WGS84', 'BD09', 'UNKNOWN'
        )),
    ADD CONSTRAINT ck_building_boundary_status
        CHECK (status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    ADD CONSTRAINT ck_building_boundary_version
        CHECK (version >= 0),
    ADD CONSTRAINT ck_building_boundary_geometry_kind
        CHECK (display_geometry IS NULL OR GeometryType(display_geometry) = 'MULTIPOLYGON');

CREATE INDEX idx_building_boundary_display_gist
    ON geo.building_boundary USING GIST (display_geometry)
    WHERE deleted_at IS NULL AND display_geometry IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 3. 边界版本修订记录
-- -----------------------------------------------------------------------------
CREATE TABLE geo.spatial_boundary_revision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(16) NOT NULL,
    entity_id UUID NOT NULL,
    boundary_id UUID NOT NULL,
    version INTEGER NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_provider VARCHAR(32),
    source_object_id VARCHAR(255),
    source_coordinate_system VARCHAR(32) NOT NULL,
    source_geometry_json JSONB NOT NULL,
    display_coordinate_system VARCHAR(32) NOT NULL,
    display_geometry geometry(MultiPolygon) NOT NULL,
    status VARCHAR(16) NOT NULL,
    change_type VARCHAR(16) NOT NULL DEFAULT 'UPSERT',
    remark TEXT,
    changed_by UUID REFERENCES core.user_account(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_spatial_boundary_revision_entity_type
        CHECK (entity_type IN ('COMMUNITY', 'BUILDING')),
    CONSTRAINT ck_spatial_boundary_revision_version
        CHECK (version >= 0),
    CONSTRAINT ck_spatial_boundary_revision_source_type
        CHECK (source_type IN ('AMAP_AOI', 'MANUAL_EDIT', 'MANUAL_DRAW', 'GEOJSON_IMPORT')),
    CONSTRAINT ck_spatial_boundary_revision_source_coordinate_system
        CHECK (source_coordinate_system IN ('GCJ02', 'WGS84', 'BD09', 'UNKNOWN')),
    CONSTRAINT ck_spatial_boundary_revision_display_coordinate_system
        CHECK (display_coordinate_system IN ('GCJ02', 'WGS84', 'BD09', 'UNKNOWN')),
    CONSTRAINT ck_spatial_boundary_revision_status
        CHECK (status IN ('UNVERIFIED', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_spatial_boundary_revision_change_type
        CHECK (change_type IN ('UPSERT', 'VERIFY', 'REJECT')),
    CONSTRAINT ck_spatial_boundary_revision_geometry_kind
        CHECK (GeometryType(display_geometry) = 'MULTIPOLYGON')
);

CREATE UNIQUE INDEX idx_spatial_boundary_revision_entity_version
    ON geo.spatial_boundary_revision (entity_type, entity_id, version);
CREATE INDEX idx_spatial_boundary_revision_changed_at
    ON geo.spatial_boundary_revision (changed_at DESC, id DESC);
CREATE INDEX idx_spatial_boundary_revision_display_gist
    ON geo.spatial_boundary_revision USING GIST (display_geometry);

-- -----------------------------------------------------------------------------
-- 4. 旧 building_geometry 查询兼容层
-- -----------------------------------------------------------------------------
CREATE VIEW geo.building_geometry AS
SELECT
    id,
    building_id,
    footprint,
    centroid,
    coordinate_reference_system,
    coordinate_source,
    spatial_quality_score,
    metadata,
    created_at,
    updated_at,
    deleted_at
FROM geo.building_boundary;

COMMENT ON TABLE geo.community_boundary IS
    '小区边界正式存储；来源坐标与前端展示坐标分离，支持人工确认和乐观锁版本';
COMMENT ON TABLE geo.building_boundary IS
    '楼栋边界正式存储；由早期 building_geometry 平滑演进，兼容旧只读查询';
COMMENT ON TABLE geo.spatial_boundary_revision IS
    '小区/楼栋边界每次写入、确认或驳回后的不可变版本快照';
COMMENT ON COLUMN geo.community_boundary.display_geometry IS
    '前端展示几何；坐标含义必须读取 display_coordinate_system，禁止仅依据 SRID 推断 WGS84';
COMMENT ON COLUMN geo.building_boundary.display_geometry IS
    '前端展示几何；坐标含义必须读取 display_coordinate_system，禁止仅依据 SRID 推断 WGS84';
