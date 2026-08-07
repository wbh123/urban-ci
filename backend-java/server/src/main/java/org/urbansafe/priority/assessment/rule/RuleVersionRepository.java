package org.urbansafe.priority.assessment.rule;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

/** 评分规则版本持久层。 */
@Repository
public class RuleVersionRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();

    public RuleVersionRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(String ruleType, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT id AS "ruleId", rule_type AS "ruleType", version_code AS "versionCode",
                       rule_name AS "ruleName", rule_content AS "ruleContent",
                       checksum AS "checksum", status AS "status",
                       activated_at AS "activatedAt", created_by AS "createdBy",
                       created_at AS "createdAt", updated_at AS "updatedAt"
                FROM core.rule_version
                WHERE deleted_at IS NULL
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (ruleType != null && !ruleType.isBlank()) {
            sql.append(" AND rule_type=:ruleType");
            params.addValue("ruleType", ruleType);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status=:status");
            params.addValue("status", status);
        }
        sql.append(" ORDER BY rule_type, created_at DESC, id DESC");
        return jdbc.query(sql.toString(), params, rowMapper).stream()
                .map(this::normalizeRuleContent).toList();
    }

    public Optional<Map<String, Object>> find(UUID ruleId) {
        return one("""
                SELECT id AS "ruleId", rule_type AS "ruleType", version_code AS "versionCode",
                       rule_name AS "ruleName", rule_content AS "ruleContent",
                       checksum AS "checksum", status AS "status",
                       activated_at AS "activatedAt", created_by AS "createdBy",
                       created_at AS "createdAt", updated_at AS "updatedAt"
                FROM core.rule_version
                WHERE id=:ruleId AND deleted_at IS NULL
                """, Map.of("ruleId", ruleId)).map(this::normalizeRuleContent);
    }

    public Optional<Map<String, Object>> lock(UUID ruleId) {
        return one("""
                SELECT id AS "ruleId", rule_type AS "ruleType", version_code AS "versionCode",
                       rule_name AS "ruleName", rule_content AS "ruleContent",
                       checksum AS "checksum", status AS "status",
                       activated_at AS "activatedAt", created_by AS "createdBy",
                       created_at AS "createdAt", updated_at AS "updatedAt"
                FROM core.rule_version
                WHERE id=:ruleId AND deleted_at IS NULL
                FOR UPDATE
                """, Map.of("ruleId", ruleId)).map(this::normalizeRuleContent);
    }

    public Optional<RuleSnapshot> findActive(String ruleType) {
        return one("""
                SELECT id AS "ruleId", rule_type AS "ruleType", version_code AS "versionCode",
                       rule_name AS "ruleName", rule_content AS "ruleContent",
                       checksum AS "checksum", status AS "status",
                       activated_at AS "activatedAt"
                FROM core.rule_version
                WHERE rule_type=:ruleType AND status='ACTIVE' AND deleted_at IS NULL
                """, Map.of("ruleType", ruleType)).map(this::toSnapshot);
    }

    public void insertDraft(
            UUID id, String ruleType, String versionCode, String ruleName,
            JsonNode content, String checksum, UUID createdBy) {
        jdbc.update("""
                INSERT INTO core.rule_version
                  (id, rule_type, version_code, rule_name, rule_content, checksum,
                   status, created_by, created_at, updated_at)
                VALUES
                  (:id,:ruleType,:versionCode,:ruleName,CAST(:content AS jsonb),:checksum,
                   'DRAFT',:createdBy,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("ruleType", ruleType)
                .addValue("versionCode", versionCode)
                .addValue("ruleName", ruleName)
                .addValue("content", content.toString())
                .addValue("checksum", checksum)
                .addValue("createdBy", createdBy));
    }

    public UUID retireActive(String ruleType) {
        List<UUID> ids = jdbc.query("""
                UPDATE core.rule_version
                SET status='RETIRED', updated_at=CURRENT_TIMESTAMP
                WHERE rule_type=:ruleType AND status='ACTIVE' AND deleted_at IS NULL
                RETURNING id
                """, Map.of("ruleType", ruleType), (rs, rowNum) -> rs.getObject(1, UUID.class));
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public void activate(UUID ruleId) {
        jdbc.update("""
                UPDATE core.rule_version
                SET status='ACTIVE', activated_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP
                WHERE id=:ruleId AND status='DRAFT' AND deleted_at IS NULL
                """, Map.of("ruleId", ruleId));
    }

    public long markCurrentAssessmentsStale(String ruleType, String reason) {
        return switch (ruleType) {
            case "COMPLETENESS" -> updateStale("core.completeness_assessment", reason)
                    + updateStale("core.risk_assessment", reason)
                    + updateStale("core.renewal_priority", reason);
            case "RISK" -> updateStale("core.risk_assessment", reason)
                    + updateStale("core.renewal_priority", reason);
            case "RENEWAL" -> updateStale("core.renewal_priority", reason);
            default -> 0;
        };
    }

    private int updateStale(String table, String reason) {
        return jdbc.update("UPDATE " + table
                + " SET status='STALE', stale_reason=:reason WHERE status='CURRENT'",
                Map.of("reason", reason));
    }

    public RuleSnapshot toSnapshot(Map<String, Object> row) {
        return new RuleSnapshot(
                uuid(row.get("ruleId")),
                String.valueOf(row.get("ruleType")),
                String.valueOf(row.get("versionCode")),
                String.valueOf(row.get("ruleName")),
                json(row.get("ruleContent")),
                String.valueOf(row.get("checksum")),
                String.valueOf(row.get("status")),
                time(row.get("activatedAt")));
    }

    private Map<String, Object> normalizeRuleContent(Map<String, Object> row) {
        Map<String, Object> normalized = new java.util.LinkedHashMap<>(row);
        normalized.put("ruleContent", objectMapper.convertValue(
                json(row.get("ruleContent")),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        return normalized;
    }

    private JsonNode json(Object value) {
        if (value instanceof JsonNode node) return node;
        try {
            return objectMapper.readTree(String.valueOf(value));
        } catch (Exception ex) {
            throw new IllegalStateException("数据库规则 JSON 无法解析", ex);
        }
    }

    private UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private OffsetDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime time) return time;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(String.valueOf(value));
    }

    private Optional<Map<String, Object>> one(String sql, Map<String, ?> params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, rowMapper));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
