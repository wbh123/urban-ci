package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;
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

    private final NamedParameterJdbcTemplate jdbc;

    public AiAutomationSettingsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean findAutoInferenceOnUpload() {
        Boolean enabled = jdbc.queryForObject("""
                SELECT boolean_value
                FROM ai.governance_setting
                WHERE setting_key=:key
                """, Map.of("key", AUTO_INFERENCE_ON_UPLOAD), Boolean.class);
        return Boolean.TRUE.equals(enabled);
    }

    public OffsetDateTime findUpdatedAt() {
        return jdbc.queryForObject("""
                SELECT updated_at
                FROM ai.governance_setting
                WHERE setting_key=:key
                """, Map.of("key", AUTO_INFERENCE_ON_UPLOAD), OffsetDateTime.class);
    }

    @Transactional
    public void updateAutoInferenceOnUpload(boolean enabled, UUID updatedBy) {
        jdbc.update("""
                INSERT INTO ai.governance_setting
                    (setting_key, boolean_value, updated_by, updated_at)
                VALUES (:key, :enabled, :updatedBy, CURRENT_TIMESTAMP)
                ON CONFLICT (setting_key) DO UPDATE
                SET boolean_value=EXCLUDED.boolean_value,
                    updated_by=EXCLUDED.updated_by,
                    updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource()
                .addValue("key", AUTO_INFERENCE_ON_UPLOAD)
                .addValue("enabled", enabled)
                .addValue("updatedBy", updatedBy));
    }
}
