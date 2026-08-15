package org.urbansafe.priority.ai.review;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 人工复核结构化修正数据持久层；正式风险评分不在此处写入。 */
@Repository
public class AiReviewCorrectionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AiReviewCorrectionRepository(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public int updateLatest(
            UUID inferenceId,
            UUID reviewedBy,
            Map<String, Object> correctedData) {
        return jdbc.update("""
                UPDATE ai.inference_review
                SET corrected_data=CAST(:correctedData AS jsonb)
                WHERE id=(
                    SELECT id
                    FROM ai.inference_review
                    WHERE inference_task_id=:inferenceId AND reviewed_by=:reviewedBy
                    ORDER BY reviewed_at DESC, id DESC
                    LIMIT 1
                )
                """, new MapSqlParameterSource()
                .addValue("inferenceId", inferenceId)
                .addValue("reviewedBy", reviewedBy)
                .addValue("correctedData", json(correctedData)));
    }

    public Optional<Map<String, Object>> latest(UUID inferenceId) {
        return jdbc.query("""
                SELECT corrected_data::text
                FROM ai.inference_review
                WHERE inference_task_id=:inferenceId
                ORDER BY reviewed_at DESC, id DESC
                LIMIT 1
                """, Map.of("inferenceId", inferenceId), rs -> {
            if (!rs.next()) return Optional.empty();
            String raw = rs.getString(1);
            if (raw == null || raw.isBlank()) return Optional.of(Map.of());
            try {
                Map<String, Object> value = objectMapper.readValue(
                        raw, new TypeReference<Map<String, Object>>() { });
                return Optional.of(new LinkedHashMap<>(value));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("人工复核修正数据解析失败", ex);
            }
        });
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("人工复核修正数据序列化失败", ex);
        }
    }
}
