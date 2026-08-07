-- UrbanSafe Priority Flyway migration V16
-- 第四阶段硬化补丁：将治理紧迫性纳入楼栋证据类型约束。
-- V15 已在部分本地数据库执行成功，因此约束修正单独追加迁移，不修改 V15 历史。

ALTER TABLE core.building_evidence
    DROP CONSTRAINT IF EXISTS building_evidence_evidence_type_check;

ALTER TABLE core.building_evidence
    ADD CONSTRAINT building_evidence_evidence_type_check
    CHECK (evidence_type IN (
        'MAINTENANCE_RECORD',
        'PROFESSIONAL_INSPECTION',
        'HISTORICAL_COMPLAINT',
        'PUBLIC_VALUE',
        'GOVERNANCE_URGENCY',
        'ENVIRONMENT_RISK',
        'OTHER'
    ));
