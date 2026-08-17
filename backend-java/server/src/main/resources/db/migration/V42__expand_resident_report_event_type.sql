-- 公众反馈复检闭环引入了更长的结构化事件名称。
-- 原 V13 定义为 VARCHAR(32)，无法容纳 RECTIFICATION_CLOSED_WITHOUT_REINSPECTION。
ALTER TABLE core.resident_report_event
    ALTER COLUMN event_type TYPE VARCHAR(64);

COMMENT ON COLUMN core.resident_report_event.event_type IS
    '公众反馈事件类型；扩展至 64 字符以支持完整的整改与复检闭环事件名称';
