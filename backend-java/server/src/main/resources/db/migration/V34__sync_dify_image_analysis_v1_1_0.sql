-- UrbanSafe Priority Flyway migration V34
-- 同步第七阶段 Dify 图片分析工作流 v1.1.0 登记版本。
-- V33 已由空间边界模型占用；本迁移仅更新模型目录版本和质量摘要，
-- 不改变 VALIDATING 状态、质量状态或正式评分证据开关。

UPDATE ai.model_registry
SET model_version = 'image-analysis-v1.1.0',
    quality_summary = jsonb_set(
        COALESCE(quality_summary, '{}'::jsonb),
        '{difyWorkflowVersion}',
        to_jsonb('image-analysis-v1.1.0'::text),
        true
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE model_code = 'AI-DIFY-WORKFLOW-001'
  AND source_platform = 'DIFY'
  AND model_version <> 'image-analysis-v1.1.0';
