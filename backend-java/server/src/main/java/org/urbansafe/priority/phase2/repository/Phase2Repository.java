package org.urbansafe.priority.phase2.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class Phase2Repository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public Phase2Repository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listCommunityPoints() {
        return jdbc.query("""
                SELECT c.id AS "communityId", c.community_name AS "communityName",
                       c.address AS "address", ST_X(l.centroid) AS "longitude",
                       ST_Y(l.centroid) AS "latitude", l.formatted_address AS "formattedAddress",
                       l.source_provider AS "provider", l.match_level AS "matchLevel"
                FROM core.community c
                LEFT JOIN geo.community_location l
                  ON l.community_id = c.id AND l.deleted_at IS NULL
                WHERE c.deleted_at IS NULL ORDER BY c.community_name
                """, Map.of(), rowMapper);
    }

    public boolean communityExists(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core.community WHERE id=:id AND deleted_at IS NULL",
                Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }

    public Map<String, Object> saveCommunityLocation(UUID communityId, double longitude,
            double latitude, String address, String provider, String level, String metadata) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("communityId", communityId).addValue("longitude", longitude)
                .addValue("latitude", latitude).addValue("address", address)
                .addValue("provider", provider).addValue("level", level)
                .addValue("metadata", metadata);
        int updated = jdbc.update("""
                UPDATE geo.community_location
                SET centroid=ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326),
                    formatted_address=:address, source_provider=:provider,
                    source_coordinate_system=CASE
                        WHEN :provider='MOCK' THEN 'UNKNOWN'
                        ELSE 'GCJ02'
                    END,
                    match_level=:level,
                    metadata=CAST(:metadata AS jsonb), collected_at=CURRENT_TIMESTAMP,
                    updated_at=CURRENT_TIMESTAMP
                WHERE community_id=:communityId AND deleted_at IS NULL
                """, p);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO geo.community_location
                      (community_id,centroid,formatted_address,source_provider,
                       source_coordinate_system,match_level,metadata)
                    VALUES (:communityId,
                      ST_SetSRID(ST_MakePoint(:longitude,:latitude),4326),
                      :address,:provider,
                      CASE WHEN :provider='MOCK' THEN 'UNKNOWN' ELSE 'GCJ02' END,
                      :level,CAST(:metadata AS jsonb))
                    """, p);
        }
        return findCommunityLocation(communityId).orElseThrow();
    }

    public Optional<Map<String, Object>> findCommunityLocation(UUID communityId) {
        return one("""
                SELECT community_id AS "communityId", ST_X(centroid) AS "longitude",
                       ST_Y(centroid) AS "latitude", formatted_address AS "formattedAddress",
                       source_provider AS "provider", source_coordinate_system AS "coordinateSystem",
                       match_level AS "matchLevel", metadata AS "metadata", updated_at AS "updatedAt"
                FROM geo.community_location
                WHERE community_id=:communityId AND deleted_at IS NULL
                """, Map.of("communityId", communityId));
    }

    public boolean buildingExists(UUID id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core.building WHERE id=:id AND deleted_at IS NULL",
                Map.of("id", id), Integer.class);
        return count != null && count > 0;
    }

    public Map<String, Object> createTask(UUID id, String code, UUID buildingId,
            String type, String title, String description, OffsetDateTime plannedAt) {
        jdbc.update("""
                INSERT INTO core.inspection_task
                  (id,task_code,building_id,inspection_type,title,description,planned_at,status,version)
                VALUES (:id,:code,:buildingId,:type,:title,:description,:plannedAt,'PENDING',0)
                """, new MapSqlParameterSource().addValue("id", id).addValue("code", code)
                .addValue("buildingId", buildingId).addValue("type", type)
                .addValue("title", title).addValue("description", description)
                .addValue("plannedAt", plannedAt));
        return findTask(id).orElseThrow();
    }

    public Optional<Map<String, Object>> findTask(UUID id) {
        return one(taskSelect() + " WHERE t.id=:id AND t.deleted_at IS NULL", Map.of("id", id));
    }

    public List<Map<String, Object>> listTasks(UUID buildingId, String status) {
        StringBuilder sql = new StringBuilder(taskSelect()).append(" WHERE t.deleted_at IS NULL");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (buildingId != null) { sql.append(" AND t.building_id=:buildingId"); p.addValue("buildingId", buildingId); }
        if (status != null && !status.isBlank()) { sql.append(" AND t.status=:status"); p.addValue("status", status); }
        sql.append(" ORDER BY t.created_at DESC");
        return jdbc.query(sql.toString(), p, rowMapper);
    }

    public int transitionTask(UUID id, String from, String to) {
        String column = switch (to) {
            case "IN_PROGRESS" -> "started_at";
            case "COMPLETED" -> "completed_at";
            case "CANCELLED" -> "cancelled_at";
            default -> null;
        };
        String timeUpdate = column == null ? "" : ", " + column + "=CURRENT_TIMESTAMP";
        return jdbc.update("UPDATE core.inspection_task SET status=:to,version=version+1," +
                        "updated_at=CURRENT_TIMESTAMP" + timeUpdate +
                        " WHERE id=:id AND status=:from AND deleted_at IS NULL",
                Map.of("id", id, "from", from, "to", to));
    }

    public int countRecords(UUID taskId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core.inspection_record WHERE inspection_task_id=:id AND deleted_at IS NULL",
                Map.of("id", taskId), Integer.class);
        return count == null ? 0 : count;
    }

    public Map<String, Object> createRecord(UUID id, UUID taskId, UUID buildingId,
            String part, String issueType, String severity, String summary,
            String suggestion, String formData) {
        jdbc.update("""
                INSERT INTO core.inspection_record
                  (id,inspection_task_id,building_id,inspection_part,inspected_at,status,
                   summary,form_data,issue_type,severity,rectification_suggestion,extra_data,version)
                VALUES (:id,:taskId,:buildingId,:part,CURRENT_TIMESTAMP,'DRAFT',:summary,
                   CAST(:formData AS jsonb),:issueType,:severity,:suggestion,'{}'::jsonb,0)
                """, new MapSqlParameterSource().addValue("id", id).addValue("taskId", taskId)
                .addValue("buildingId", buildingId).addValue("part", part)
                .addValue("summary", summary).addValue("formData", formData)
                .addValue("issueType", issueType).addValue("severity", severity)
                .addValue("suggestion", suggestion));
        return findRecord(id).orElseThrow();
    }

    public Optional<Map<String, Object>> findRecord(UUID id) {
        return one(recordSelect() + " WHERE r.id=:id AND r.deleted_at IS NULL", Map.of("id", id));
    }

    public List<Map<String, Object>> listRecords(UUID taskId) {
        return jdbc.query(recordSelect() +
                " WHERE r.inspection_task_id=:taskId AND r.deleted_at IS NULL ORDER BY r.created_at DESC",
                Map.of("taskId", taskId), rowMapper);
    }

    public Map<String, Object> createAsset(UUID id, String bucket, String objectKey,
            String filename, String contentType, long size, String sha256,
            String provider, String etag, String businessType, UUID businessId, String role) {
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("id", id)
                .addValue("bucket", bucket).addValue("objectKey", objectKey)
                .addValue("filename", filename).addValue("contentType", contentType)
                .addValue("size", size).addValue("sha256", sha256).addValue("provider", provider)
                .addValue("etag", etag).addValue("businessType", businessType)
                .addValue("businessId", businessId).addValue("role", role);
        jdbc.update("""
                INSERT INTO asset.file_asset
                  (id,bucket_name,object_key,original_filename,content_type,file_size,sha256,
                   business_type,business_id,upload_status,storage_provider,object_etag)
                VALUES (:id,:bucket,:objectKey,:filename,:contentType,:size,:sha256,
                   :businessType,:businessId,'AVAILABLE',:provider,:etag)
                """, p);
        jdbc.update("""
                INSERT INTO asset.asset_binding(asset_id,business_type,business_id,binding_role)
                VALUES (:id,:businessType,:businessId,:role)
                """, p);
        return findAsset(id).orElseThrow();
    }

    public Optional<Map<String, Object>> findAsset(UUID id) {
        return one("""
                SELECT id AS "assetId",bucket_name AS "bucket",object_key AS "objectKey",
                       original_filename AS "originalFilename",content_type AS "contentType",
                       file_size AS "fileSize",storage_provider AS "storageProvider",created_at AS "createdAt"
                FROM asset.file_asset
                WHERE id=:id AND deleted_at IS NULL AND upload_status='AVAILABLE'
                """, Map.of("id", id));
    }

    public List<Map<String, Object>> listAssets(String businessType, UUID businessId) {
        return jdbc.query("""
                SELECT a.id AS "assetId",a.original_filename AS "originalFilename",
                       a.content_type AS "contentType",a.file_size AS "fileSize",
                       a.storage_provider AS "storageProvider",b.binding_role AS "bindingRole",
                       a.created_at AS "createdAt"
                FROM asset.asset_binding b JOIN asset.file_asset a ON a.id=b.asset_id
                WHERE b.business_type=:businessType AND b.business_id=:businessId
                  AND b.deleted_at IS NULL AND a.deleted_at IS NULL
                ORDER BY a.created_at DESC
                """, Map.of("businessType", businessType, "businessId", businessId), rowMapper);
    }

    public String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? Map.of() : value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("JSON 字段序列化失败", ex); }
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try { return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper)); }
        catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private String taskSelect() {
        return """
                SELECT t.id AS "taskId",t.task_code AS "taskCode",t.building_id AS "buildingId",
                       b.building_name AS "buildingName",b.community_id AS "communityId",
                       t.inspection_type AS "inspectionType",t.title AS "title",
                       t.description AS "description",t.planned_at AS "plannedAt",
                       t.status AS "status",t.started_at AS "startedAt",t.completed_at AS "completedAt",
                       t.cancelled_at AS "cancelledAt",t.version AS "version",t.created_at AS "createdAt"
                FROM core.inspection_task t JOIN core.building b ON b.id=t.building_id
                """;
    }

    private String recordSelect() {
        return """
                SELECT r.id AS "recordId",r.inspection_task_id AS "taskId",r.building_id AS "buildingId",
                       r.inspection_part AS "inspectionPart",r.issue_type AS "issueType",
                       r.severity AS "severity",r.summary AS "summary",
                       r.rectification_suggestion AS "rectificationSuggestion",r.form_data AS "formData",
                       r.status AS "status",r.inspected_at AS "inspectedAt",r.created_at AS "createdAt"
                FROM core.inspection_record r
                """;
    }
}
