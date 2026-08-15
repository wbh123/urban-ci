-- Spring AI 智能编排执行轨迹（Tool Execution Trace，非模型私有思维过程）。
-- 只记录工具调用步骤与最终状态；不记录完整图片 Base64 / API Key / 模型隐藏推理过程。

CREATE TABLE ai.agent_execution (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_type      VARCHAR(64)  NOT NULL,
    business_id        UUID,
    question           TEXT         NOT NULL,
    status             VARCHAR(32)  NOT NULL, -- PENDING/RUNNING/SUCCEEDED/PARTIAL_SUCCEEDED/FAILED
    requested_by       UUID,
    requested_by_name  VARCHAR(128),
    model_code         VARCHAR(64),
    started_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at        TIMESTAMPTZ,
    duration_ms        BIGINT,
    summary            TEXT,
    error_code         VARCHAR(64),
    error_message      TEXT,
    version            INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE ai.agent_execution_step (
    id           BIGSERIAL PRIMARY KEY,
    execution_id UUID         NOT NULL REFERENCES ai.agent_execution(id),
    seq_no       INTEGER      NOT NULL,
    type         VARCHAR(32)  NOT NULL, -- TOOL / LLM
    tool_name    VARCHAR(128),
    provider     VARCHAR(64),
    status       VARCHAR(32)  NOT NULL, -- WAITING/RUNNING/SUCCEEDED/FAILED/SKIPPED
    duration_ms  BIGINT,
    error_code   VARCHAR(64),
    detail       TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_execution_step_execution ON ai.agent_execution_step (execution_id);
CREATE INDEX idx_agent_execution_business ON ai.agent_execution (business_type, business_id, started_at DESC);
