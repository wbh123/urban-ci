package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 人工智能自动化设置持久层。 */
@Repository
public class AiAutomationSettingsRepository {

    static final String AUTO_INFERENCE_ON_UPLOAD = "AUTO_INFERENCE_ON_UPLOAD";
    static final String INTELLIGENT_WORKFLOW_ENABLED = "INTELLIGENT_WORKFLOW_ENABLED";
    static final String KNOWLEDGE_QA_ENABLED = "KNOWLEDGE_QA_ENABLED";
    static final String DEFAULT_VISION_MODEL_ID = "DEFAULT_VISION_MODEL_ID";
    private static final String FALLBACK_VISION_MODEL_ID = "AI-VISION-LOCAL-001";

    private final NamedParameterJdbcTemplate jdbc;

    public AiAutomationSettingsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean findAutoInferenceOnUpload() {
        return findBoolean(AUTO_INFERENCE_ON_UPLOAD, false);
    }

    /** 比赛阶段默认启用智能工作流；数据库显式关闭时以数据库为准。 */
    public boolean findIntelligentWorkflowEnabled() {
        return findBoolean(INTELLIGENT_WORKFLOW_ENABLED, true);
    }

    /** 比赛阶段默认启用知识问答；数据库显式关闭时以数据库为准。 */
    public boolean findKnowledgeQaEnabled() {
        return findBoolean(KNOWLEDGE_QA_ENABLED, true);
    }

    public String findDefaultVisionModelId(String fallback) {
        List<String> values = jdbc.query("""
                SELECT text_value
                FROM ai.governance_setting
                WHERE setting_key=:key AND text_value IS NOT NULL
                """, Map.of("key", DEFAULT_VISION_MODEL_ID),
                (rs, rowNum) -> rs.getString("text_value"));
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            return fallback;
        }
        return values.get(0).trim();
    }

    public OffsetDateTime findUpdatedAt() {
        List<OffsetDateTime> values = jdbc.query("""
                SELECT updated_at
                FROM ai.governance_setting
                WHERE setting_key IN (:keys)
                ORDER BY updated_at DESC
                LIMIT 1
                """, Map.of("keys", List.of(
                        AUTO_INFERENCE_ON_UPLOAD,
                        INTELLIGENT_WORKFLOW_ENABLED,
                        KNOWLEDGE_QA_ENABLED,
                        DEFAULT_VISION_MODEL_ID)),
                (rs, rowNum) -> rs.getObject("updated_at", OffsetDateTime.class));
        return values.isEmpty() ? null : values.get(0);
    }

    /** 旧调用兼容：只更新三个布尔开关时保持当前默认视觉模型不变。 */
    @Transactional
    public void update(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            UUID updatedBy) {
        update(
                autoInferenceOnUpload,
                intelligentWorkflowEnabled,
                knowledgeQaEnabled,
                findDefaultVisionModelId(FALLBACK_VISION_MODEL_ID),
                updatedBy);
    }

    @Transactional
    public void update(
            boolean autoInferenceOnUpload,
            boolean intelligentWorkflowEnabled,
            boolean knowledgeQaEnabled,
            String defaultVisionModelId,
            UUID updatedBy) {
        upsertBoolean(AUTO_INFERENCE_ON_UPLOAD, autoInferenceOnUpload, updatedBy);
        upsertBoolean(INTELLIGENT_WORKFLOW_ENABLED, intelligentWorkflowEnabled, updatedBy);
        upsertBoolean(KNOWLEDGE_QA_ENABLED, knowledgeQaEnabled, updatedBy);
        upsertText(DEFAULT_VISION_MODEL_ID, defaultVisionModelId, updatedBy);
    }

    private boolean findBoolean(String key, boolean defaultValue) {
        List<Boolean> values = jdbc.query("""
                SELECT boolean_value
                FROM ai.governance_setting
                WHERE setting_key=:key AND boolean_value IS NOT NULL
                """, Map.of("key", key),
                (rs, rowNum) -> rs.getBoolean("boolean_value"));
        return values.isEmpty() ? defaultValue : Boolean.TRUE.equals(values.get(0));
    }

    private void upsertBoolean(String key, boolean enabled, UUID updatedBy) {
        jdbc.update("""
                INSERT INTO ai.governance_setting
                    (setting_key, boolean_value, text_value, updated_by, updated_at)
                VALUES (:key, :enabled, NULL, :updatedBy, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO UPDATE
                SET boolean_value=EXCLUDED.boolean_value,
                    text_value=NULL,
                    updated_by=EXCLUDED.updated_by,
                    updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("enabled", enabled)
                .addValue("updatedBy", updatedBy));
    }

    private void upsertText(String key, String value, UUID updatedBy) {
        jdbc.update("""
                INSERT INTO ai.governance_setting
                    (setting_key, boolean_value, text_value, updated_by, updated_at)
                VALUES (:key, NULL, :value, :updatedBy, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO UPDATE
                SET boolean_value=NULL,
                    text_value=EXCLUDED.text_value,
                    updated_by=EXCLUDED.updated_by,
                    updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("key", key)
                .addValue("value", value)
                .addValue("updatedBy", updatedBy));
    }
}
