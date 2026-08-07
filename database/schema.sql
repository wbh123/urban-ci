-- UrbanSafe Priority 旧数据库初始化入口（已停用）
--
-- 数据库结构唯一事实来源：
--   backend-java/server/src/main/resources/db/migration/
--
-- 正确初始化流程：
--   1. 启动 docker/docker-compose.yml 中的 PostgreSQL；
--   2. 构建并启动 starter/target/Service.jar；
--   3. Spring Boot 自动执行 Flyway 并维护 flyway_schema_history。
--
-- 禁止把本文件重新挂载到 /docker-entrypoint-initdb.d，
-- 也禁止直接执行迁移 SQL 后再启动 Flyway。

\echo 'ERROR: database/schema.sql 已停用，请通过 Spring Boot Flyway 初始化数据库。'
\quit 1
