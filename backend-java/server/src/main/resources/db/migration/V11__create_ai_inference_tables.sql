-- UrbanSafe Priority Flyway migration V11
-- 第三阶段：人工智能辅助识别闭环。
-- 新增推理任务、推理结果、检测对象与人工复核表，并扩展模型登记表以支持 MOCK/REAL 模式与准入字段。
--
-- 冲突说明：V4 已创建 ai.model_registry、ai.inference_job、ai.defect_result、ai.defect_review、
-- ai.embedding 占位表。第三阶段设计文档提出的 ai.model_registry 与 V4 同名，
-- 按“当前 Flyway 为准”原则复用并扩展 ai.model_registry；推理任务/结果/检测/复核使用新表名
-- （inference_task/inference_result/detection/inference_review），避免修改 V1~V10 已冻结迁移。
-- V4 的 inference_job、defect_result、defect_review、embedding 保持未使用占位状态。

-- 1. 扩展模型登记表：增加推理模式、来源、权重摘要、类别定义、限制与准入时间。
ALTER TABLE ai.model_registry
    ADD COLUMN mode VARCHAR(16) NOT NULL DEFAULT 'MOCK'
        CHECK (mode IN ('MOCK', 'REAL')),
    ADD COLUMN source_platform VARCHAR(64),
    ADD COLUMN source_resource_id VARCHAR(128),
    ADD COLUMN source_revision VARCHAR(128),
    ADD COLUMN weight_filename VARCHAR(255),
    ADD COLUMN weight_sha256 CHAR(64),
    ADD COLUMN supported_classes JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN limitations JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN approved_at TIMESTAMPTZ;

-- 业务模型编号在未删除记录中唯一。
CREATE UNIQUE INDEX uk_ai_model_registry_model_code
    ON ai.model_registry (model_code) WHERE deleted_at IS NULL;

-- 2. 登记确定性 MOCK 模型（与 FastAPI DeterministicMockAdapter 对齐）。
INSERT INTO ai.model_registry (
    model_code, model_name, model_version, model_type, framework, source_type,
    license_name, input_spec, output_spec, model_config, status,
    mode, source_platform, supported_classes, limitations
) VALUES (
    'AI-DEFECT-MOCK-001',
    'UrbanSafe Deterministic Mock Detector',
    '0.1.0',
    'OBJECT_DETECTION',
    'DeterministicMockAdapter',
    'MOCK',
    'PROJECT-INTERNAL-MOCK',
    '{"contentTypes":["image/jpeg","image/png","image/webp"],"maxFileSizeMb":10}'::jsonb,
    '{"coordinateType":"NORMALIZED_XYWH","classes":["crack"]}'::jsonb,
    '{"note":"模拟结果仅用于业务链路验证，不代表真实模型能力","offline":true}'::jsonb,
    'MOCK',
    'MOCK',
    'PROJECT-INTERNAL',
    '["crack"]'::jsonb,
    '["模拟结果仅用于业务链路验证","模型置信度不等于房屋危险概率","不作为正式房屋安全鉴定结论"]'::jsonb
) ON CONFLICT DO NOTHING;

-- 3. 推理任务：一次图片推理请求的生命周期。
CREATE TABLE ai.inference_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    request_code VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128),
    asset_id UUID NOT NULL REFERENCES asset.file_asset(id),
    inspection_task_id UUID REFERENCES core.inspection_task(id),
    inspection_record_id UUID REFERENCES core.inspection_record(id),
    building_id UUID NOT NULL REFERENCES core.building(id),
    community_id UUID NOT NULL REFERENCES core.community(id),
    model_registry_id UUID NOT NULL REFERENCES ai.model_registry(id),
    mode VARCHAR(16) NOT NULL CHECK (mode IN ('MOCK', 'REAL')),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED')),
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    review_status VARCHAR(32) NOT NULL DEFAULT 'UNREVIEWED'
        CHECK (review_status IN ('UNREVIEWED', 'CONFIRMED', 'CORRECTED', 'REJECTED')),
    requested_by UUID NOT NULL REFERENCES core.user_account(id),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT CHECK (duration_ms IS NULL OR duration_ms >= 0),
    error_code VARCHAR(64),
    error_message VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_inference_task_completed_status
        CHECK ((status IN ('SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED') AND completed_at IS NOT NULL)
            OR status IN ('PENDING', 'RUNNING')),
    CONSTRAINT ck_ai_inference_task_succeeded_duration
        CHECK (status <> 'SUCCEEDED' OR duration_ms IS NOT NULL),
    CONSTRAINT ck_ai_inference_task_failed_error
        CHECK (status NOT IN ('FAILED', 'REJECTED') OR error_code IS NOT NULL)
);

CREATE UNIQUE INDEX uk_ai_inference_task_request_code
    ON ai.inference_task (request_code);

-- 幂等：同一用户+图片+模式+模型+幂等键只能有一个活跃任务。
CREATE UNIQUE INDEX uk_ai_inference_task_idempotency
    ON ai.inference_task (requested_by, asset_id, mode, model_registry_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL AND status IN ('PENDING', 'RUNNING');

-- 无显式幂等键时，同一用户+图片+模式+模型只能有一个活跃任务。
CREATE UNIQUE INDEX uk_ai_inference_task_active
    ON ai.inference_task (requested_by, asset_id, mode, model_registry_id)
    WHERE idempotency_key IS NULL AND status IN ('PENDING', 'RUNNING');

-- 4. 推理结果：与推理任务一对一。
CREATE TABLE ai.inference_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inference_task_id UUID NOT NULL UNIQUE REFERENCES ai.inference_task(id),
    image_width INTEGER CHECK (image_width IS NULL OR image_width > 0),
    image_height INTEGER CHECK (image_height IS NULL OR image_height > 0),
    quality_status VARCHAR(32),
    applicability VARCHAR(32)
        CHECK (applicability IS NULL OR applicability IN (
            'APPLICABLE', 'NO_DEFECT_FOUND', 'LOW_QUALITY', 'NOT_APPLICABLE', 'INVALID_IMAGE')),
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    raw_output_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    warning_messages JSONB NOT NULL DEFAULT '[]'::jsonb,
    result_asset_id UUID REFERENCES asset.file_asset(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 5. 检测对象：归一化检测框，受坐标与置信度约束。
CREATE TABLE ai.detection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inference_result_id UUID NOT NULL REFERENCES ai.inference_result(id),
    sequence_no INTEGER NOT NULL CHECK (sequence_no > 0),
    class_code VARCHAR(64) NOT NULL,
    class_name VARCHAR(128) NOT NULL,
    confidence NUMERIC(6, 5) NOT NULL CHECK (confidence >= 0 AND confidence <= 1),
    bbox_x NUMERIC(6, 5) NOT NULL CHECK (bbox_x >= 0 AND bbox_x <= 1),
    bbox_y NUMERIC(6, 5) NOT NULL CHECK (bbox_y >= 0 AND bbox_y <= 1),
    bbox_width NUMERIC(6, 5) NOT NULL CHECK (bbox_width > 0 AND bbox_width <= 1),
    bbox_height NUMERIC(6, 5) NOT NULL CHECK (bbox_height > 0 AND bbox_height <= 1),
    coordinate_type VARCHAR(32) NOT NULL DEFAULT 'NORMALIZED_XYWH',
    extra_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (inference_result_id, sequence_no),
    CONSTRAINT ck_ai_detection_bbox_x_width CHECK (bbox_x + bbox_width <= 1),
    CONSTRAINT ck_ai_detection_bbox_y_height CHECK (bbox_y + bbox_height <= 1)
);

-- 6. 人工复核历史：保留每次复核记录。
CREATE TABLE ai.inference_review (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inference_task_id UUID NOT NULL REFERENCES ai.inference_task(id),
    review_status VARCHAR(32) NOT NULL
        CHECK (review_status IN ('CONFIRMED', 'CORRECTED', 'REJECTED')),
    review_comment TEXT,
    reviewed_by UUID NOT NULL REFERENCES core.user_account(id),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    corrected_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_inference_task_asset ON ai.inference_task (asset_id);
CREATE INDEX idx_ai_inference_task_building ON ai.inference_task (building_id);
CREATE INDEX idx_ai_inference_task_status ON ai.inference_task (status);
CREATE INDEX idx_ai_inference_task_requested_by ON ai.inference_task (requested_by);
CREATE INDEX idx_ai_inference_review_task ON ai.inference_review (inference_task_id);

COMMENT ON TABLE ai.inference_task IS '人工智能推理任务，记录输入图片、模型、模式、状态与追溯关系';
COMMENT ON TABLE ai.inference_result IS '推理标准化结果，与推理任务一对一，保存质量、适用性与原始快照';
COMMENT ON TABLE ai.detection IS '推理检测对象，使用归一化左上角宽高检测框';
COMMENT ON TABLE ai.inference_review IS '人工复核历史，仅修正人工智能结果，不形成正式房屋安全鉴定结论';
COMMENT ON COLUMN ai.inference_task.mode IS '推理模式：MOCK 模拟、REAL 真实模型（需 APPROVED）';
COMMENT ON COLUMN ai.detection.bbox_x IS '归一化检测框左上角 X，范围 [0,1]，x+width<=1';
