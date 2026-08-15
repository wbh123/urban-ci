package org.urbansafe.priority.feedback.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackClosureRepository {
    private static final String UUID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
    public FeedbackClosureRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Map<String, Object>> latestReinspection(UUID reportId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT t.id AS "taskId", t.task_code AS "taskCode", t.building_id AS "buildingId",
                           t.inspection_type AS "inspectionType", t.status AS "status", t.planned_at AS "plannedAt",
                           t.started_at AS "startedAt", t.completed_at AS "completedAt",
                           EXISTS (
                             SELECT 1
                             FROM core.resident_report_event result_event
                             WHERE result_event.resident_report_id=e.resident_report_id
                               AND result_event.event_type IN ('REINSPECTION_PASSED','REINSPECTION_FAILED')
                               AND COALESCE(result_event.event_data->>'taskId','')=COALESCE(e.event_data->>'taskId','')
                               AND result_event.created_at>=e.created_at
                           ) AS "resultRecorded",
                           e.created_at AS "linkedAt"
                    FROM core.resident_report_event e
                    JOIN core.inspection_task t
                      ON t.id=CASE
                           WHEN COALESCE(e.event_data->>'taskId','') ~ :uuidPattern
                           THEN (e.event_data->>'taskId')::uuid ELSE NULL END
                     AND t.deleted_at IS NULL
                    WHERE e.resident_report_id=:reportId
                      AND e.event_type='REINSPECTION_CREATED'
                      AND jsonb_exists(e.event_data, 'taskId')
                    ORDER BY e.created_at DESC, e.id DESC LIMIT 1
                    """, Map.of("reportId", reportId, "uuidPattern", UUID_PATTERN), rowMapper));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }
}
