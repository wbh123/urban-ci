-- UrbanSafe Priority Flyway migration V17
-- 第四阶段硬化补丁：历史结果允许同一楼栋同一规则版本多次计算，
-- 当前结果唯一性由 V14 的 partial unique indexes 负责。

DROP INDEX IF EXISTS core.uk_completeness_building_version;
DROP INDEX IF EXISTS core.uk_risk_building_version;
DROP INDEX IF EXISTS core.uk_renewal_building_version;

CREATE INDEX IF NOT EXISTS idx_completeness_building_version
    ON core.completeness_assessment (building_id, assessment_version, assessed_at DESC);

CREATE INDEX IF NOT EXISTS idx_risk_building_version
    ON core.risk_assessment (building_id, assessment_version, assessed_at DESC);

CREATE INDEX IF NOT EXISTS idx_renewal_building_version_scope
    ON core.renewal_priority (building_id, priority_version, ranking_scope_key, generated_at DESC);
