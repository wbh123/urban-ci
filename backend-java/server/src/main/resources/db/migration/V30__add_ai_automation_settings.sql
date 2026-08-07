-- UrbanSafe Priority Flyway migration V30
-- 增加人工智能自动化设置，默认关闭上传后自动识别，避免未经管理员确认产生第三方调用与费用。

CREATE TABLE IF NOT EXISTS ai.governance_setting (
    setting_key VARCHAR(64) PRIMARY KEY,
    boolean_value BOOLEAN NOT NULL,
    updated_by UUID,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ai.governance_setting (setting_key, boolean_value)
VALUES ('AUTO_INFERENCE_ON_UPLOAD', FALSE)
ON CONFLICT (setting_key) DO NOTHING;

COMMENT ON TABLE ai.governance_setting IS '人工智能治理运行设置，不保存密钥、供应商地址或模型权重路径';
COMMENT ON COLUMN ai.governance_setting.setting_key IS '稳定设置编号';
COMMENT ON COLUMN ai.governance_setting.boolean_value IS '布尔设置值';
COMMENT ON COLUMN ai.governance_setting.updated_by IS '最后修改人员，仅用于审计追溯';
