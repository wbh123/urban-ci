-- UrbanSafe Priority Flyway migration V22
-- 报告追溯需要在推理任务上保存模型编码和版本快照，避免模型登记信息后续变化。

ALTER TABLE ai.inference_task
    ADD COLUMN IF NOT EXISTS model_code VARCHAR(128),
    ADD COLUMN IF NOT EXISTS model_version VARCHAR(64);

UPDATE ai.inference_task t
SET model_code = m.model_code,
    model_version = m.model_version
FROM ai.model_registry m
WHERE m.id = t.model_registry_id
  AND (t.model_code IS NULL OR t.model_version IS NULL);

ALTER TABLE ai.inference_task
    ALTER COLUMN model_code SET NOT NULL,
    ALTER COLUMN model_version SET NOT NULL;

CREATE OR REPLACE FUNCTION ai.fill_inference_model_identity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    SELECT model_code, model_version
    INTO NEW.model_code, NEW.model_version
    FROM ai.model_registry
    WHERE id = NEW.model_registry_id AND deleted_at IS NULL;

    IF NEW.model_code IS NULL OR NEW.model_version IS NULL THEN
        RAISE EXCEPTION 'AI model registry entry % is unavailable', NEW.model_registry_id;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_inference_model_identity ON ai.inference_task;
CREATE TRIGGER trg_inference_model_identity
BEFORE INSERT OR UPDATE OF model_registry_id
ON ai.inference_task
FOR EACH ROW
EXECUTE FUNCTION ai.fill_inference_model_identity();

CREATE INDEX IF NOT EXISTS idx_inference_task_model_identity
    ON ai.inference_task(model_code, model_version, created_at DESC);

COMMENT ON COLUMN ai.inference_task.model_code IS
    '推理任务创建时固定的模型业务编码快照，用于报告追溯';
COMMENT ON COLUMN ai.inference_task.model_version IS
    '推理任务创建时固定的模型版本快照，用于报告追溯';
