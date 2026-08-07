-- UrbanSafe Priority Flyway migration V23
-- 第五阶段：FAILED 报告必须保留排障，但不得阻塞同源报告重新生成。

DROP INDEX IF EXISTS asset.uk_generated_report_idempotency_active;

CREATE UNIQUE INDEX uk_generated_report_idempotency_active
    ON asset.generated_report(idempotency_key)
    WHERE deleted_at IS NULL
      AND report_status IN ('GENERATING', 'GENERATED', 'STALE');
