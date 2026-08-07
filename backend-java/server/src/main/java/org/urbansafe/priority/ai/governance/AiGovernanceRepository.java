package org.urbansafe.priority.ai.governance;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 只读人工智能治理统计仓储。 */
@Repository
public class AiGovernanceRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AiGovernanceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, AiProviderMetrics> providerMetrics(int days) {
        String sql = """
                SELECT COALESCE(provider_code, 'LEGACY') AS provider_code,
                       COUNT(*) AS total_tasks,
                       COUNT(*) FILTER (WHERE status='SUCCEEDED') AS succeeded_tasks,
                       COUNT(*) FILTER (WHERE status IN ('FAILED','REJECTED')) AS failed_tasks,
                       COUNT(*) FILTER (
                           WHERE review_status IN ('CONFIRMED','CORRECTED','REJECTED')
                       ) AS reviewed_tasks,
                       COUNT(*) FILTER (
                           WHERE status='SUCCEEDED' AND review_status='UNREVIEWED'
                       ) AS pending_review_tasks,
                       COALESCE(ROUND(AVG(duration_ms)
                           FILTER (WHERE duration_ms IS NOT NULL)), 0) AS average_duration_ms
                FROM ai.inference_task
                WHERE requested_at >= CURRENT_TIMESTAMP - (:days * INTERVAL '1 day')
                GROUP BY COALESCE(provider_code, 'LEGACY')
                ORDER BY provider_code
                """;
        Map<String, AiProviderMetrics> result = new LinkedHashMap<>();
        jdbc.query(sql, Map.of("days", days), rs -> {
            String providerCode = rs.getString("provider_code");
            long total = rs.getLong("total_tasks");
            long succeeded = rs.getLong("succeeded_tasks");
            result.put(providerCode, new AiProviderMetrics(
                    total,
                    succeeded,
                    rs.getLong("failed_tasks"),
                    rs.getLong("reviewed_tasks"),
                    rs.getLong("pending_review_tasks"),
                    rs.getLong("average_duration_ms"),
                    total == 0 ? 0d : succeeded * 100d / total));
        });
        return result;
    }
}
