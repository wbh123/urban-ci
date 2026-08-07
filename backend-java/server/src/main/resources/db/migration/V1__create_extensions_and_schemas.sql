-- UrbanSafe Priority Flyway migration V1
-- 创建 PostgreSQL 扩展和业务 Schema。

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS geo;
CREATE SCHEMA IF NOT EXISTS ai;
CREATE SCHEMA IF NOT EXISTS asset;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS integration;

COMMENT ON SCHEMA core IS '城安智序核心业务数据';
COMMENT ON SCHEMA geo IS '城安智序 PostGIS 空间数据';
COMMENT ON SCHEMA ai IS '城安智序模型、推理结果和向量数据';
COMMENT ON SCHEMA asset IS '城安智序 MinIO 文件及报告元数据';
COMMENT ON SCHEMA audit IS '城安智序操作与调用审计数据';
COMMENT ON SCHEMA integration IS '城安智序异步事件与任务编排数据';
