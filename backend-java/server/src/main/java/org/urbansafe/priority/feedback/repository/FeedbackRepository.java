package org.urbansafe.priority.feedback.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 公众反馈持久层，集中维护公开查询与内部处理所需的受控 SQL。 */
@Repository
public class FeedbackRepository {

    private static final String FEEDBACK_BUSINESS_TYPE = "RESIDENT_REPORT";
    private static final String FEEDBACK_IMAGE_ROLE = "FEEDBACK_PHOTO";

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public FeedbackRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listPublicCommunities() {
        return jdbc.query("""
                SELECT id AS "communityId", community_name AS "communityName",
                       administrative_region AS "administrativeRegion", address AS "address"
                FROM core.community
                WHERE deleted_at IS NULL AND status='ACTIVE'
                ORDER BY community_name
                """, Map.of(), rowMapper);
    }

    public List<Map<String, Object>> listPublicBuildings(UUID communityId) {
        return jdbc.query("""
                SELECT id AS "buildingId", building_code AS "buildingCode",
                       COALESCE(building_name, building_code) AS "buildingName", address AS "address"
                FROM core.building
                WHERE community_id=:communityId AND deleted_at IS NULL AND status='ACTIVE'
                ORDER BY building_code
                """, Map.of("communityId", communityId), rowMapper);
    }

    public boolean communityExists(UUID communityId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM core.community
                WHERE id=:id AND deleted_at IS NULL AND status='ACTIVE'
                """, Map.of("id", communityId), Long.class);
        return count != null && count > 0;
    }

    public boolean buildingBelongsToCommunity(UUID buildingId, UUID communityId) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM core.building
                WHERE id=:buildingId AND community_id=:communityId
                  AND deleted_at IS NULL AND status='ACTIVE'
                """, Map.of("buildingId", buildingId, "communityId", communityId), Long.class);
        return count != null && count > 0;
    }

    @Transactional
    public void insertReport(Map<String, Object> report) {
        jdbc.update("""
                INSERT INTO core.resident_report (
                    id, report_code, community_id, building_id, reporter_user_id,
                    reporter_name, contact_phone, contact_email, contact_consent,
                    feedback_channel, location_text, recorded_by, report_type,
                    description, status, urgency, evidence, submitted_at,
                    tracking_secret_hash, updated_by
                ) VALUES (
                    :id, :reportCode, :communityId, :buildingId, :reporterUserId,
                    :reporterName, :contactPhone, :contactEmail, :contactConsent,
                    :feedbackChannel, :locationText, :recordedBy, :reportType,
                    :description, 'SUBMITTED', :urgency, CAST(:evidence AS jsonb),
                    CURRENT_TIMESTAMP, :trackingSecretHash, :updatedBy
                )
                """, new MapSqlParameterSource(report));
    }

    @Transactional
    public void insertEvent(UUID reportId, String eventType, String fromStatus, String toStatus,
            String message, String visibility, String actorType, UUID actorUserId,
            Map<String, Object> eventData) {
        jdbc.update("""
                INSERT INTO core.resident_report_event (
                    id, resident_report_id, event_type, from_status, to_status,
                    message, visibility, actor_type, actor_user_id, event_data
                ) VALUES (
                    :id, :reportId, :eventType, :fromStatus, :toStatus,
                    :message, :visibility, :actorType, :actorUserId, CAST(:eventData AS jsonb)
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("reportId", reportId)
                .addValue("eventType", eventType)
                .addValue("fromStatus", fromStatus)
                .addValue("toStatus", toStatus)
                .addValue("message", message)
                .addValue("visibility", visibility)
                .addValue("actorType", actorType)
                .addValue("actorUserId", actorUserId)
                .addValue("eventData", json(eventData)));
    }

    public Optional<Map<String, Object>> findPublicReport(String reportCode, String trackingHash) {
        return one("""
                SELECT r.id AS "reportId", r.report_code AS "reportCode",
                       r.report_type AS "reportType", r.description AS "description",
                       r.status AS "status", r.urgency AS "urgency",
                       r.feedback_channel AS "feedbackChannel", r.reporter_name AS "reporterName",
                       r.contact_phone AS "contactPhone", r.contact_email AS "contactEmail",
                       r.location_text AS "locationText", r.handling_summary AS "handlingSummary",
                       r.submitted_at AS "submittedAt", r.handled_at AS "handledAt",
                       r.closed_at AS "closedAt", c.community_name AS "communityName",
                       COALESCE(b.building_name, b.building_code) AS "buildingName"
                FROM core.resident_report r
                JOIN core.community c ON c.id=r.community_id
                LEFT JOIN core.building b ON b.id=r.building_id
                WHERE r.report_code=:reportCode AND r.tracking_secret_hash=:trackingHash
                  AND r.deleted_at IS NULL
                """, Map.of("reportCode", reportCode, "trackingHash", trackingHash));
    }

    /**
     * 为公众图片上传锁定反馈行。锁持续到外层事务结束，使“检查数量 + 新增绑定”串行执行。
     */
    public Optional<Map<String, Object>> lockPublicReport(String reportCode, String trackingHash) {
        return one("""
                SELECT id AS "reportId", report_code AS "reportCode", status AS "status"
                FROM core.resident_report
                WHERE report_code=:reportCode AND tracking_secret_hash=:trackingHash
                  AND deleted_at IS NULL
                FOR UPDATE
                """, Map.of("reportCode", reportCode, "trackingHash", trackingHash));
    }

    public List<Map<String, Object>> listPublicEvents(UUID reportId) {
        return jdbc.query("""
                SELECT event_type AS "eventType", from_status AS "fromStatus",
                       to_status AS "toStatus", message AS "message", created_at AS "createdAt"
                FROM core.resident_report_event
                WHERE resident_report_id=:reportId AND visibility='PUBLIC'
                ORDER BY created_at, id
                """, Map.of("reportId", reportId), rowMapper);
    }

    public Optional<Map<String, Object>> findReport(UUID reportId) {
        return one("""
                SELECT id AS "reportId", report_code AS "reportCode", status AS "status",
                       feedback_channel AS "feedbackChannel", report_type AS "reportType",
                       urgency AS "urgency", description AS "description", location_text AS "locationText",
                       community_id AS "communityId", building_id AS "buildingId"
                FROM core.resident_report
                WHERE id=:id AND deleted_at IS NULL
                """, Map.of("id", reportId));
    }

    /**
     * 为整改闭环、复检任务创建和复验结论锁定反馈工单。
     * 锁持续到外层事务结束，避免“人工免复检”和“创建/完成复检任务”并发交叉。
     */
    public Optional<Map<String, Object>> lockReport(UUID reportId) {
        return one("""
                SELECT id AS "reportId", report_code AS "reportCode", status AS "status",
                       feedback_channel AS "feedbackChannel", report_type AS "reportType",
                       urgency AS "urgency", description AS "description", location_text AS "locationText",
                       community_id AS "communityId", building_id AS "buildingId"
                FROM core.resident_report
                WHERE id=:id AND deleted_at IS NULL
                FOR UPDATE
                """, Map.of("id", reportId));
    }

    public int countReportImages(UUID reportId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM asset.asset_binding binding
                JOIN asset.file_asset asset ON asset.id=binding.asset_id
                WHERE binding.business_type=:businessType
                  AND binding.business_id=:reportId
                  AND binding.binding_role=:bindingRole
                  AND binding.deleted_at IS NULL
                  AND asset.deleted_at IS NULL
                  AND asset.upload_status='AVAILABLE'
                """, Map.of(
                        "businessType", FEEDBACK_BUSINESS_TYPE,
                        "reportId", reportId,
                        "bindingRole", FEEDBACK_IMAGE_ROLE), Integer.class);
        return count == null ? 0 : count;
    }

    public List<Map<String, Object>> listReportImages(UUID reportId) {
        return jdbc.query("""
                SELECT asset.id AS "assetId",
                       asset.original_filename AS "originalFilename",
                       asset.content_type AS "contentType",
                       asset.file_size AS "fileSize",
                       asset.created_at AS "createdAt"
                FROM asset.asset_binding binding
                JOIN asset.file_asset asset ON asset.id=binding.asset_id
                WHERE binding.business_type=:businessType
                  AND binding.business_id=:reportId
                  AND binding.binding_role=:bindingRole
                  AND binding.deleted_at IS NULL
                  AND asset.deleted_at IS NULL
                  AND asset.upload_status='AVAILABLE'
                ORDER BY asset.created_at, asset.id
                """, Map.of(
                        "businessType", FEEDBACK_BUSINESS_TYPE,
                        "reportId", reportId,
                        "bindingRole", FEEDBACK_IMAGE_ROLE), rowMapper);
    }

    public boolean assetBelongsToReport(UUID reportId, UUID assetId) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                FROM asset.asset_binding binding
                JOIN asset.file_asset asset ON asset.id=binding.asset_id
                WHERE binding.asset_id=:assetId
                  AND binding.business_type=:businessType
                  AND binding.business_id=:reportId
                  AND binding.binding_role=:bindingRole
                  AND binding.deleted_at IS NULL
                  AND asset.deleted_at IS NULL
                  AND asset.upload_status='AVAILABLE'
                """, Map.of(
                        "assetId", assetId,
                        "businessType", FEEDBACK_BUSINESS_TYPE,
                        "reportId", reportId,
                        "bindingRole", FEEDBACK_IMAGE_ROLE), Integer.class);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> listReports(String status, String channel, UUID communityId,
            int page, int size) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.id AS "reportId", r.report_code AS "reportCode",
                       r.report_type AS "reportType", r.description AS "description",
                       r.status AS "status", r.urgency AS "urgency",
                       r.feedback_channel AS "feedbackChannel", r.reporter_name AS "reporterName",
                       r.contact_phone AS "contactPhone", r.location_text AS "locationText",
                       r.handling_summary AS "handlingSummary", r.submitted_at AS "submittedAt",
                       c.id AS "communityId", c.community_name AS "communityName",
                       b.id AS "buildingId", COALESCE(b.building_name, b.building_code) AS "buildingName",
                       (SELECT count(*) FROM asset.asset_binding ab
                         JOIN asset.file_asset fa ON fa.id=ab.asset_id
                        WHERE ab.business_type='RESIDENT_REPORT' AND ab.business_id=r.id
                          AND ab.binding_role='FEEDBACK_PHOTO' AND ab.deleted_at IS NULL
                          AND fa.deleted_at IS NULL AND fa.upload_status='AVAILABLE') AS "imageCount"
                FROM core.resident_report r
                JOIN core.community c ON c.id=r.community_id
                LEFT JOIN core.building b ON b.id=r.building_id
                WHERE r.deleted_at IS NULL
                """);
        MapSqlParameterSource params = filters(status, channel, communityId);
        appendFilters(sql, status, channel, communityId);
        sql.append(" ORDER BY r.submitted_at DESC LIMIT :size OFFSET :offset");
        params.addValue("size", size).addValue("offset", page * size);
        return jdbc.query(sql.toString(), params, rowMapper);
    }

    public long countReports(String status, String channel, UUID communityId) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) FROM core.resident_report r
                WHERE r.deleted_at IS NULL
                """);
        MapSqlParameterSource params = filters(status, channel, communityId);
        appendFilters(sql, status, channel, communityId);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0L : count;
    }

    @Transactional
    public int updateStatus(UUID reportId, String status, String handlingSummary, UUID updatedBy) {
        return jdbc.update("""
                UPDATE core.resident_report
                SET status=:status,
                    handling_summary=COALESCE(:handlingSummary, handling_summary),
                    handled_at=CASE WHEN :status IN ('ACCEPTED','PROCESSING','NEED_MORE_INFO','RESOLVED','CLOSED')
                                    THEN COALESCE(handled_at, CURRENT_TIMESTAMP) ELSE handled_at END,
                    closed_at=CASE WHEN :status='CLOSED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                    updated_by=:updatedBy, updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND deleted_at IS NULL
                """, new MapSqlParameterSource()
                .addValue("id", reportId)
                .addValue("status", status)
                .addValue("handlingSummary", handlingSummary)
                .addValue("updatedBy", updatedBy));
    }

    private MapSqlParameterSource filters(String status, String channel, UUID communityId) {
        return new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("channel", channel)
                .addValue("communityId", communityId);
    }

    private void appendFilters(StringBuilder sql, String status, String channel, UUID communityId) {
        if (status != null) {
            sql.append(" AND r.status=:status");
        }
        if (channel != null) {
            sql.append(" AND r.feedback_channel=:channel");
        }
        if (communityId != null) {
            sql.append(" AND r.community_id=:communityId");
        }
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("反馈事件序列化失败", ex);
        }
    }

    public static Map<String, Object> mutableMap() {
        return new LinkedHashMap<>();
    }
}
