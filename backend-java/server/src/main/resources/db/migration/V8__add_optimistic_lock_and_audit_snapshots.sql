-- UrbanSafe Priority Flyway migration V8
-- 为第一阶段可修改资源增加乐观锁版本号，并补齐统一审计所需的变更前后快照。

ALTER TABLE core.community
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE core.building
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE core.building_evidence
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE core.user_account
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE core.building
    ADD CONSTRAINT ck_building_population_relation
    CHECK (
        elderly_count <= resident_count
        AND child_count <= resident_count
        AND elderly_count + child_count <= resident_count
    );

ALTER TABLE audit.operation_log
    ADD COLUMN before_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN after_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN changed_fields JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN core.community.version IS 'MyBatis-Plus 乐观锁版本号；每次成功更新后递增';
COMMENT ON COLUMN core.building.version IS 'MyBatis-Plus 乐观锁版本号；每次成功更新后递增';
COMMENT ON COLUMN core.building_evidence.version IS 'MyBatis-Plus 乐观锁版本号；每次成功更新后递增';
COMMENT ON COLUMN core.user_account.version IS 'MyBatis-Plus 乐观锁版本号；每次成功更新后递增';
COMMENT ON COLUMN audit.operation_log.before_data IS '脱敏后的操作前资源快照；无快照时保存空对象';
COMMENT ON COLUMN audit.operation_log.after_data IS '脱敏后的操作后资源快照；无快照时保存空对象';
COMMENT ON COLUMN audit.operation_log.changed_fields IS '发生变化的字段名数组；无变化字段时保存空数组';
