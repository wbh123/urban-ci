package org.urbansafe.priority.feedback.service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.exception.InvalidRequestException;

@Service
public class FeedbackManagementQueryService {
    private static final Set<String> STATUSES = Set.of("SUBMITTED", "ACCEPTED", "PROCESSING", "NEED_MORE_INFO", "RESOLVED", "CLOSED", "REJECTED", "CANCELLED");
    private static final Set<String> CHANNELS = Set.of("WEB", "PHONE", "SMS", "COUNTER", "INTERNAL");
    private final NamedParameterJdbcTemplate jdbc;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
    public FeedbackManagementQueryService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String, Object> list(String status, String channel, UUID communityId, UUID buildingId,
            String keyword, String submittedFrom, String submittedTo, int page, int size) {
        String normalizedStatus = upper(status); String normalizedChannel = upper(channel); String normalizedKeyword = text(keyword);
        OffsetDateTime from = time(submittedFrom, "submittedFrom"); OffsetDateTime to = time(submittedTo, "submittedTo");
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) throw new InvalidRequestException("FEEDBACK_STATUS_INVALID", "反馈状态无效");
        if (normalizedChannel != null && !CHANNELS.contains(normalizedChannel)) throw new InvalidRequestException("FEEDBACK_CHANNEL_INVALID", "反馈渠道无效");
        if (page < 0 || size < 1 || size > 100) throw new InvalidRequestException("PAGINATION_INVALID", "分页参数范围为 page>=0 且 1<=size<=100");
        if (from != null && to != null && from.isAfter(to)) throw new InvalidRequestException("FEEDBACK_DATE_RANGE_INVALID", "提交时间范围无效");
        MapSqlParameterSource params = params(normalizedStatus, normalizedChannel, communityId, buildingId, normalizedKeyword, from, to);
        String where = where(normalizedStatus, normalizedChannel, communityId, buildingId, normalizedKeyword, from, to);
        String listSql = """
                SELECT r.id AS "reportId", r.report_code AS "reportCode", r.report_type AS "reportType", r.description AS "description",
                       r.status AS "status", r.urgency AS "urgency", r.feedback_channel AS "feedbackChannel", r.reporter_name AS "reporterName",
                       r.contact_phone AS "contactPhone", r.location_text AS "locationText", r.handling_summary AS "handlingSummary", r.submitted_at AS "submittedAt",
                       c.id AS "communityId", c.community_name AS "communityName", b.id AS "buildingId", COALESCE(b.building_name,b.building_code) AS "buildingName",
                       (SELECT count(*) FROM asset.asset_binding ab JOIN asset.file_asset fa ON fa.id=ab.asset_id
                         WHERE ab.business_type='RESIDENT_REPORT' AND ab.business_id=r.id AND ab.binding_role='FEEDBACK_PHOTO'
                           AND ab.deleted_at IS NULL AND fa.deleted_at IS NULL AND fa.upload_status='AVAILABLE') AS "imageCount",
                       rt.id AS "reinspectionTaskId", rt.task_code AS "reinspectionTaskCode", rt.status AS "reinspectionStatus"
                FROM core.resident_report r JOIN core.community c ON c.id=r.community_id LEFT JOIN core.building b ON b.id=r.building_id
                LEFT JOIN LATERAL (
                    SELECT CASE WHEN COALESCE(e.event_data->>'taskId','') ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
                                THEN (e.event_data->>'taskId')::uuid ELSE NULL END AS task_id
                    FROM core.resident_report_event e
                    WHERE e.resident_report_id=r.id AND e.event_type='REINSPECTION_CREATED' AND jsonb_exists(e.event_data,'taskId')
                    ORDER BY e.created_at DESC,e.id DESC LIMIT 1
                ) latest_reinspection ON TRUE
                LEFT JOIN core.inspection_task rt ON rt.id=latest_reinspection.task_id AND rt.deleted_at IS NULL
                WHERE r.deleted_at IS NULL
                """ + where + " ORDER BY r.submitted_at DESC LIMIT :size OFFSET :offset";
        params.addValue("size", size).addValue("offset", page * size);
        List<Map<String,Object>> content = jdbc.query(listSql, params, rowMapper);
        Long totalValue = jdbc.queryForObject("SELECT count(*) FROM core.resident_report r WHERE r.deleted_at IS NULL" + where, params, Long.class);
        long total = totalValue == null ? 0L : totalValue;
        Map<String,Object> metadata = new LinkedHashMap<>(); metadata.put("page",page); metadata.put("size",size); metadata.put("totalElements",total); metadata.put("totalPages",(int)Math.ceil((double)total/size));
        return Map.of("content",content,"page",metadata);
    }
    private MapSqlParameterSource params(String status,String channel,UUID communityId,UUID buildingId,String keyword,OffsetDateTime from,OffsetDateTime to) {
        return new MapSqlParameterSource().addValue("status",status).addValue("channel",channel).addValue("communityId",communityId).addValue("buildingId",buildingId)
                .addValue("keyword",keyword==null?null:"%"+keyword.toLowerCase()+"%").addValue("submittedFrom",from).addValue("submittedTo",to);
    }
    private String where(String status,String channel,UUID communityId,UUID buildingId,String keyword,OffsetDateTime from,OffsetDateTime to) {
        StringBuilder sql=new StringBuilder(); if(status!=null)sql.append(" AND r.status=:status"); if(channel!=null)sql.append(" AND r.feedback_channel=:channel");
        if(communityId!=null)sql.append(" AND r.community_id=:communityId"); if(buildingId!=null)sql.append(" AND r.building_id=:buildingId");
        if(keyword!=null)sql.append(" AND (lower(r.report_code) LIKE :keyword OR lower(r.description) LIKE :keyword OR lower(COALESCE(r.location_text,'')) LIKE :keyword)");
        if(from!=null)sql.append(" AND r.submitted_at>=:submittedFrom"); if(to!=null)sql.append(" AND r.submitted_at<=:submittedTo"); return sql.toString();
    }
    private String upper(String value){String normalized=text(value);return normalized==null?null:normalized.toUpperCase();}
    private String text(String value){if(value==null)return null;String normalized=value.trim();return normalized.isEmpty()?null:normalized;}
    private OffsetDateTime time(String value,String field){String normalized=text(value);if(normalized==null)return null;try{return OffsetDateTime.parse(normalized);}catch(DateTimeParseException ex){throw new InvalidRequestException("FEEDBACK_DATE_INVALID",field+" 必须为 ISO-8601 时间");}}
}
