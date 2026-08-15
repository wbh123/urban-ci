package org.urbansafe.priority.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AiRichDetectionDetailService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AiRichDetectionDetailService(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> enrich(UUID inferenceId, Map<String, Object> source) {
        String raw = jdbc.query(
                "SELECT raw_output_snapshot::text FROM ai.inference_result WHERE inference_task_id=:id ORDER BY created_at DESC LIMIT 1",
                Map.of("id", inferenceId),
                (rs, rowNum) -> rs.getString(1)).stream().findFirst().orElse(null);
        return enrichSnapshot(source, raw, mapper);
    }

    static Map<String, Object> enrichSnapshot(
            Map<String, Object> source,
            String raw,
            ObjectMapper mapper) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        if (raw == null || raw.isBlank()) return result;
        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode model = root.path("model");
            if (model.isObject()) {
                String modelId = model.path("modelId").asText("");
                String modelName = model.path("modelName").asText("");
                String modelVersion = model.path("version").asText("");
                if (!modelId.isBlank()) result.put("modelId", modelId);
                if (!modelName.isBlank()) result.put("modelName", modelName);
                if (!modelVersion.isBlank()) result.put("modelVersion", modelVersion);
            }

            JsonNode detections = root.path("detections");
            if (detections.isArray()) {
                result.put("detections", mapper.convertValue(detections, Object.class));
                result.put("detectionCount", detections.size());
            }
        } catch (Exception ignored) {
            // 历史快照解析失败时保留基础详情与数据库检测框。
        }
        return result;
    }
}
