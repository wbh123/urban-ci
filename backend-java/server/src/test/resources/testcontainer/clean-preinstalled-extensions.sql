-- ============================================================================
-- Testcontainers 数据库前置清理脚本
--
-- 固定的 PostGIS bundle 镜像会在初始化数据库时自动创建 PostGIS 相关扩展对象，
-- 这会让 public schema 在 Flyway 首次接管前处于非空状态。项目生产配置明确禁止
-- baselineOnMigrate，因此测试必须先移除镜像自动创建的对象，再由 Flyway V1 统一创建。
--
-- 下列 DROP 只作用于每次测试新建的临时数据库，不接触开发或生产数据库。
-- CASCADE 用于同步移除 PostGIS 自动创建的 topology、tiger 等依赖 schema 和对象。
-- ============================================================================

DROP EXTENSION IF EXISTS postgis_tiger_geocoder CASCADE;
DROP EXTENSION IF EXISTS postgis_topology CASCADE;
DROP EXTENSION IF EXISTS postgis_sfcgal CASCADE;
DROP EXTENSION IF EXISTS postgis_raster CASCADE;
DROP EXTENSION IF EXISTS postgis CASCADE;
DROP EXTENSION IF EXISTS address_standardizer_data_us CASCADE;
DROP EXTENSION IF EXISTS address_standardizer CASCADE;
DROP EXTENSION IF EXISTS vector CASCADE;
DROP EXTENSION IF EXISTS pgcrypto CASCADE;

-- bundle 镜像可能随版本增加其他预装对象；最终重建 public，保证 Flyway 首次接管时确实为空。
DROP SCHEMA public CASCADE;
CREATE SCHEMA public AUTHORIZATION CURRENT_USER;
GRANT ALL ON SCHEMA public TO PUBLIC;
