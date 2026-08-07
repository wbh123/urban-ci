-- UrbanSafe Priority Flyway migration V29
-- 同步第七阶段 Dify 图片分析工作流固定测试版本。
-- 仅更新模型目录版本和质量摘要，不改变 VALIDATING 状态或正式评分证据开关。

UPDATE ai.model_registry
SET model_version = 'image-analysis-v1.0.1',
    quality_summary = jsonb_set(
        COALESCE(quality_summary, '{}'::jsonb),
        '{difyWorkflowVersion}',
        to_jsonb('image-analysis-v1.0.1'::text),
        true
    ),
    updated_at = CURRENT_TIMESTAMP
WHERE model_code = 'AI-DIFY-WORKFLOW-001'
  AND source_platform = 'DIFY'
  AND model_version <> 'image-analysis-v1.0.1';
