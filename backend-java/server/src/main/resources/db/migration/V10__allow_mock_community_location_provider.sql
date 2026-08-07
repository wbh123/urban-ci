-- UrbanSafe Priority Flyway migration V10
-- 模拟地图模式会生成可供本地演示使用的坐标，必须明确记录为 MOCK，不能伪装成 MANUAL。

DO $$
DECLARE
    provider_constraint_name TEXT;
BEGIN
    SELECT conname
      INTO provider_constraint_name
      FROM pg_constraint
     WHERE conrelid = 'geo.community_location'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%source_provider%'
     LIMIT 1;

    IF provider_constraint_name IS NOT NULL THEN
        EXECUTE format(
            'ALTER TABLE geo.community_location DROP CONSTRAINT %I',
            provider_constraint_name
        );
    END IF;
END
$$;

ALTER TABLE geo.community_location
    ADD CONSTRAINT ck_community_location_source_provider
    CHECK (source_provider IN ('AMAP', 'MANUAL', 'IMPORT', 'MOCK'));

COMMENT ON COLUMN geo.community_location.source_provider IS
    '位置来源：AMAP 高德地理编码、MANUAL 人工确认、IMPORT 结构化导入、MOCK 本地模拟坐标';
