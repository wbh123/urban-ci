-- R2 空间边界开发演示数据。
-- 重要：以下 Polygon 均为人工合成的开发轮廓，不代表真实测绘、真实高德 AOI 或任何真实小区边界。
-- 坐标仅用于验证 GCJ-02 -> 前端显示的数据链路，禁止用于生产业务判断。

BEGIN;

DO $$
BEGIN
    IF to_regclass('geo.community_boundary') IS NULL
       OR to_regclass('geo.building_boundary') IS NULL
       OR to_regclass('geo.spatial_boundary_revision') IS NULL THEN
        RAISE EXCEPTION 'R2 空间边界迁移尚未完成，请先让 Flyway 执行 V33';
    END IF;
END $$;

WITH demo_admin AS (
    SELECT id
    FROM core.user_account
    WHERE username='demo_admin' AND deleted_at IS NULL
    LIMIT 1
), community_data(community_code, geometry_json, source_object_id) AS (
    VALUES
        ('DEMO-COMMUNITY-001',
         '{"type":"MultiPolygon","coordinates":[[[[113.13280,27.82680],[113.13520,27.82680],[113.13520,27.82860],[113.13280,27.82860],[113.13280,27.82680]]]]}'::jsonb,
         'DEMO-AOI-COMMUNITY-001'),
        ('DEMO-COMMUNITY-002',
         '{"type":"MultiPolygon","coordinates":[[[[113.11520,27.83220],[113.11780,27.83220],[113.11780,27.83410],[113.11520,27.83410],[113.11520,27.83220]]]]}'::jsonb,
         'DEMO-AOI-COMMUNITY-002')
)
INSERT INTO geo.community_boundary (
    community_id,
    source_type,
    source_provider,
    source_object_id,
    source_coordinate_system,
    source_geometry_json,
    display_coordinate_system,
    display_geometry,
    status,
    version,
    verified_by,
    verified_at,
    remark,
    metadata
)
SELECT
    c.id,
    'MANUAL_DRAW',
    'DEMO_SEED',
    d.source_object_id,
    'GCJ02',
    d.geometry_json,
    'GCJ02',
    ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(d.geometry_json::text), 0)),
    'VERIFIED',
    1,
    a.id,
    TIMESTAMPTZ '2026-08-07 23:50:00+08',
    'DEMO_DATA：人工合成开发轮廓，仅验证地图边界链路，不代表真实测绘边界',
    '{"demo":true,"syntheticBoundary":true,"productionEligible":false}'::jsonb
FROM community_data d
JOIN core.community c
  ON c.community_code=d.community_code AND c.deleted_at IS NULL
CROSS JOIN demo_admin a
ON CONFLICT (community_id) WHERE deleted_at IS NULL DO UPDATE SET
    source_type=EXCLUDED.source_type,
    source_provider=EXCLUDED.source_provider,
    source_object_id=EXCLUDED.source_object_id,
    source_coordinate_system=EXCLUDED.source_coordinate_system,
    source_geometry_json=EXCLUDED.source_geometry_json,
    display_coordinate_system=EXCLUDED.display_coordinate_system,
    display_geometry=EXCLUDED.display_geometry,
    status=EXCLUDED.status,
    version=EXCLUDED.version,
    verified_by=EXCLUDED.verified_by,
    verified_at=EXCLUDED.verified_at,
    remark=EXCLUDED.remark,
    metadata=EXCLUDED.metadata,
    updated_at=TIMESTAMPTZ '2026-08-07 23:50:00+08';

WITH demo_admin AS (
    SELECT id
    FROM core.user_account
    WHERE username='demo_admin' AND deleted_at IS NULL
    LIMIT 1
), building_data(building_code, geometry_json, source_object_id) AS (
    VALUES
        ('A-01', '{"type":"MultiPolygon","coordinates":[[[[113.13305,27.82702],[113.13348,27.82702],[113.13348,27.82734],[113.13305,27.82734],[113.13305,27.82702]]]]}'::jsonb, 'DEMO-BUILDING-A-01'),
        ('A-02', '{"type":"MultiPolygon","coordinates":[[[[113.13370,27.82702],[113.13415,27.82702],[113.13415,27.82736],[113.13370,27.82736],[113.13370,27.82702]]]]}'::jsonb, 'DEMO-BUILDING-A-02'),
        ('A-03', '{"type":"MultiPolygon","coordinates":[[[[113.13440,27.82705],[113.13486,27.82705],[113.13486,27.82739],[113.13440,27.82739],[113.13440,27.82705]]]]}'::jsonb, 'DEMO-BUILDING-A-03'),
        ('B-01', '{"type":"MultiPolygon","coordinates":[[[[113.11558,27.83255],[113.11608,27.83255],[113.11608,27.83291],[113.11558,27.83291],[113.11558,27.83255]]]]}'::jsonb, 'DEMO-BUILDING-B-01'),
        ('B-02', '{"type":"MultiPolygon","coordinates":[[[[113.11642,27.83258],[113.11694,27.83258],[113.11694,27.83295],[113.11642,27.83295],[113.11642,27.83258]]]]}'::jsonb, 'DEMO-BUILDING-B-02')
)
INSERT INTO geo.building_boundary (
    building_id,
    source_type,
    source_provider,
    source_object_id,
    source_coordinate_system,
    source_geometry_json,
    display_coordinate_system,
    display_geometry,
    status,
    version,
    verified_by,
    verified_at,
    remark,
    metadata
)
SELECT
    b.id,
    'MANUAL_DRAW',
    'DEMO_SEED',
    d.source_object_id,
    'GCJ02',
    d.geometry_json,
    'GCJ02',
    ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(d.geometry_json::text), 0)),
    'VERIFIED',
    1,
    a.id,
    TIMESTAMPTZ '2026-08-07 23:50:00+08',
    'DEMO_DATA：人工合成开发轮廓，仅验证地图边界链路，不代表真实测绘边界',
    '{"demo":true,"syntheticBoundary":true,"productionEligible":false}'::jsonb
FROM building_data d
JOIN core.building b
  ON b.building_code=d.building_code AND b.deleted_at IS NULL
CROSS JOIN demo_admin a
ON CONFLICT (building_id) WHERE deleted_at IS NULL DO UPDATE SET
    source_type=EXCLUDED.source_type,
    source_provider=EXCLUDED.source_provider,
    source_object_id=EXCLUDED.source_object_id,
    source_coordinate_system=EXCLUDED.source_coordinate_system,
    source_geometry_json=EXCLUDED.source_geometry_json,
    display_coordinate_system=EXCLUDED.display_coordinate_system,
    display_geometry=EXCLUDED.display_geometry,
    status=EXCLUDED.status,
    version=EXCLUDED.version,
    verified_by=EXCLUDED.verified_by,
    verified_at=EXCLUDED.verified_at,
    remark=EXCLUDED.remark,
    metadata=EXCLUDED.metadata,
    updated_at=TIMESTAMPTZ '2026-08-07 23:50:00+08';

-- 为演示边界补一份版本 1 快照。重复执行时保持幂等。
INSERT INTO geo.spatial_boundary_revision (
    entity_type, entity_id, boundary_id, version,
    source_type, source_provider, source_object_id,
    source_coordinate_system, source_geometry_json,
    display_coordinate_system, display_geometry,
    status, change_type, remark, changed_by, changed_at
)
SELECT
    'COMMUNITY', cb.community_id, cb.id, cb.version,
    cb.source_type, cb.source_provider, cb.source_object_id,
    cb.source_coordinate_system, cb.source_geometry_json,
    cb.display_coordinate_system, cb.display_geometry,
    cb.status, 'VERIFY', cb.remark, cb.verified_by, cb.verified_at
FROM geo.community_boundary cb
JOIN core.community c ON c.id=cb.community_id
WHERE c.community_code LIKE 'DEMO-COMMUNITY-%'
  AND cb.deleted_at IS NULL
  AND cb.display_geometry IS NOT NULL
ON CONFLICT (entity_type, entity_id, version) DO UPDATE SET
    boundary_id=EXCLUDED.boundary_id,
    source_type=EXCLUDED.source_type,
    source_provider=EXCLUDED.source_provider,
    source_object_id=EXCLUDED.source_object_id,
    source_coordinate_system=EXCLUDED.source_coordinate_system,
    source_geometry_json=EXCLUDED.source_geometry_json,
    display_coordinate_system=EXCLUDED.display_coordinate_system,
    display_geometry=EXCLUDED.display_geometry,
    status=EXCLUDED.status,
    change_type=EXCLUDED.change_type,
    remark=EXCLUDED.remark,
    changed_by=EXCLUDED.changed_by,
    changed_at=EXCLUDED.changed_at;

INSERT INTO geo.spatial_boundary_revision (
    entity_type, entity_id, boundary_id, version,
    source_type, source_provider, source_object_id,
    source_coordinate_system, source_geometry_json,
    display_coordinate_system, display_geometry,
    status, change_type, remark, changed_by, changed_at
)
SELECT
    'BUILDING', bb.building_id, bb.id, bb.version,
    bb.source_type, bb.source_provider, bb.source_object_id,
    bb.source_coordinate_system, bb.source_geometry_json,
    bb.display_coordinate_system, bb.display_geometry,
    bb.status, 'VERIFY', bb.remark, bb.verified_by, bb.verified_at
FROM geo.building_boundary bb
JOIN core.building b ON b.id=bb.building_id
WHERE b.remark LIKE 'DEMO_DATA%'
  AND bb.deleted_at IS NULL
  AND bb.display_geometry IS NOT NULL
ON CONFLICT (entity_type, entity_id, version) DO UPDATE SET
    boundary_id=EXCLUDED.boundary_id,
    source_type=EXCLUDED.source_type,
    source_provider=EXCLUDED.source_provider,
    source_object_id=EXCLUDED.source_object_id,
    source_coordinate_system=EXCLUDED.source_coordinate_system,
    source_geometry_json=EXCLUDED.source_geometry_json,
    display_coordinate_system=EXCLUDED.display_coordinate_system,
    display_geometry=EXCLUDED.display_geometry,
    status=EXCLUDED.status,
    change_type=EXCLUDED.change_type,
    remark=EXCLUDED.remark,
    changed_by=EXCLUDED.changed_by,
    changed_at=EXCLUDED.changed_at;

COMMIT;
