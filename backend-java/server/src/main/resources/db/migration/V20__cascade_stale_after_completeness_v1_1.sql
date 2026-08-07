-- UrbanSafe Priority Flyway migration V20
-- 第四阶段冻结修复：COMPLETENESS-V1.1 激活后，级联标记依赖旧完整度结果的风险和更新优先级为过期。
--
-- V19 已将旧完整度 CURRENT 结果置为 STALE，但数据库迁移不会经过 RuleVersionService，
-- 因此需要在此补齐与服务层相同的依赖级联。迁移仅处理 CURRENT，重复执行不会修改历史结果。

UPDATE core.risk_assessment r
SET status = 'STALE',
    stale_reason = 'RULE_CHANGED:COMPLETENESS-V1.1',
    updated_at = CURRENT_TIMESTAMP
WHERE r.status = 'CURRENT'
  AND EXISTS (
      SELECT 1
      FROM core.completeness_assessment ca
      JOIN core.rule_version rv ON rv.id = ca.rule_version_id
      WHERE ca.id = r.completeness_assessment_id
        AND (
            ca.status <> 'CURRENT'
            OR rv.rule_type <> 'COMPLETENESS'
            OR rv.version_code <> 'COMPLETENESS-V1.1'
        )
  );

UPDATE core.renewal_priority p
SET status = 'STALE',
    stale_reason = 'RULE_CHANGED:COMPLETENESS-V1.1'
WHERE p.status = 'CURRENT'
  AND EXISTS (
      SELECT 1
      FROM core.risk_assessment r
      JOIN core.completeness_assessment ca ON ca.id = r.completeness_assessment_id
      JOIN core.rule_version rv ON rv.id = ca.rule_version_id
      WHERE r.id = p.risk_assessment_id
        AND (
            r.status <> 'CURRENT'
            OR ca.status <> 'CURRENT'
            OR rv.rule_type <> 'COMPLETENESS'
            OR rv.version_code <> 'COMPLETENESS-V1.1'
        )
  );
