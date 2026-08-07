-- UrbanSafe Priority Flyway migration V18
-- 第四阶段：RENEWAL-V1.1 激活后补标旧更新优先级 CURRENT 结果为过期。
--
-- V15 已退役 RENEWAL-V1 并激活 RENEWAL-V1.1，但已存在的旧版本 CURRENT
-- renewal_priority 结果不会自动过期。该迁移只处理旧 RENEWAL 规则版本的 CURRENT
-- 结果，保留 RENEWAL-V1.1 当前结果，也不影响历史 STALE 或 SUPERSEDED 结果。

UPDATE core.renewal_priority p
SET status = 'STALE',
    stale_reason = 'RULE_CHANGED:RENEWAL-V1.1'
WHERE p.status = 'CURRENT'
  AND EXISTS (
      SELECT 1
      FROM core.rule_version rv
      WHERE rv.id = p.rule_version_id
        AND rv.rule_type = 'RENEWAL'
        AND rv.version_code <> 'RENEWAL-V1.1'
  );
