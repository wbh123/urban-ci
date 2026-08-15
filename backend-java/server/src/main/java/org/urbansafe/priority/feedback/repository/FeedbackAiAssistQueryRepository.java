package org.urbansafe.priority.feedback.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 公众反馈 AI 辅助只读查询。
 *
 * <p>该仓储只读取形成辅助归类所需的业务字段，不提供任何状态更新或写入能力。
 */
@Repository
public class FeedbackAiAssistQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public FeedbackAiAssistQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Map<String, Object>> findReport(UUID reportId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT r.id AS "reportId", r.report_code AS "reportCode",
                           r.report_type AS "reportType", r.urgency AS "urgency",
                           r.description AS "description", r.location_text AS "locationText",
                           r.status AS "status", r.community_id AS "communityId",
                           r.building_id AS "buildingId",
                           c.community_name AS "communityName",
                           COALESCE(b.building_name, b.building_code) AS "buildingName"
                    FROM core.resident_report r
                    JOIN core.community c ON c.id=r.community_id
                    LEFT JOIN core.building b ON b.id=r.building_id
                    WHERE r.id=:reportId AND r.deleted_at IS NULL
                    """, Map.of("reportId", reportId), rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
