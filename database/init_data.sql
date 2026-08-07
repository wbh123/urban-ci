-- UrbanSafe Priority 旧基础数据入口（已停用）
--
-- 基础角色、占位模型和规则版本由 Flyway 迁移
-- V6__insert_reference_data.sql 统一写入，并由 flyway_schema_history 记录。

\echo 'ERROR: database/init_data.sql 已停用，请通过 Spring Boot Flyway 写入基础数据。'
\quit 1
