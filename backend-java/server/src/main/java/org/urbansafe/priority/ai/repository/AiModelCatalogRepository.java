package org.urbansafe.priority.ai.repository;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 查询业务侧模型登记，不接触模型权重和运行时目录。 */
@Repository
public class AiModelCatalogRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public AiModelCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> listVisibleModels() {
        return jdbc.query("""
                SELECT model_code AS "modelId",
                       model_name AS "modelName",
                       model_version AS "modelVersion",
                       model_type AS "modelType",
                       mode AS "mode",
                       status AS "status",
                       license_name AS "license",
                       supported_classes AS "supportedClasses",
                       limitations AS "limitations",
                       deployment_stage AS "deploymentStage",
                       formal_evidence_enabled AS "formalEvidenceEnabled",
                       quality_status AS "qualityStatus",
                       quality_summary AS "qualitySummary",
                       quality_evaluated_at AS "qualityEvaluatedAt",
                       COALESCE(model_config ->> 'providerCode', 'FAST_API') AS "providerCode",
                       COALESCE(model_config ->> 'capabilityType',
                           CASE WHEN model_type LIKE '%WORKFLOW%' THEN 'WORKFLOW' ELSE 'VISION_INFERENCE' END
                       ) AS "capabilityType"
                FROM ai.model_registry
                WHERE deleted_at IS NULL
                  AND status IN ('MOCK', 'APPROVED')
                  AND deployment_stage <> 'SUSPENDED'
                ORDER BY CASE mode WHEN 'REAL' THEN 0 ELSE 1 END,
                         model_code
                """, Map.of(), rowMapper);
    }
}
