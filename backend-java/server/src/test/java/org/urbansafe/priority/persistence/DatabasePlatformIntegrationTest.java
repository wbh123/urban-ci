package org.urbansafe.priority.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.entity.OperationLogEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.repository.OperationLogRepository;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 使用真实 PostgreSQL/Testcontainers 验证数据库扩展、Flyway、分页与乐观锁基础能力。
 */
class DatabasePlatformIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CommunityMapper communityMapper;

    /** 统一审计日志仓库，用于验证 PostgreSQL INET 与 JSONB 的真实写入行为。 */
    @Autowired
    private OperationLogRepository operationLogRepository;

    /** 验证 V1 至当前最新 V34 均已成功执行。 */
    @Test
    void flywayShouldApplyThroughLatestVersion() {
        List<String> versions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",
                String.class);

        assertThat(versions).contains(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", "34");
        assertThat(versions.getLast()).isEqualTo("34");
    }

    @Test
    void firstRealModelShouldRemainInQualityValidation() {
        Map<String, Object> model = jdbcTemplate.queryForMap("""
                SELECT deployment_stage, quality_status, formal_evidence_enabled
                FROM ai.model_registry
                WHERE model_code='AI-CRACK-HF-UNET-001'
                """);

        assertThat(model.get("deployment_stage")).isEqualTo("VALIDATING");
        assertThat(model.get("quality_status")).isEqualTo("VALIDATING");
        assertThat(model.get("formal_evidence_enabled")).isEqualTo(false);
    }

    @Test
    void difyWorkflowShouldBeRegisteredForBusinessUseButNotFormalEvidence() {
        Map<String, Object> model = jdbcTemplate.queryForMap("""
                SELECT model_version, deployment_stage, quality_status, formal_evidence_enabled
                FROM ai.model_registry
                WHERE model_code='AI-DIFY-WORKFLOW-001'
                """);

        assertThat(model.get("model_version")).isEqualTo("image-analysis-v1.1.0");
        assertThat(model.get("deployment_stage")).isEqualTo("VALIDATING");
        assertThat(model.get("quality_status")).isEqualTo("VALIDATING");
        assertThat(model.get("formal_evidence_enabled")).isEqualTo(false);
    }

    /** 验证固定测试镜像预装 PostGIS、vector 与 pgcrypto。 */
    @Test
    void requiredPostgresqlExtensionsShouldBeAvailable() {
        List<String> extensions = jdbcTemplate.queryForList(
                "SELECT extname FROM pg_extension WHERE extname IN ('postgis', 'vector', 'pgcrypto')",
                String.class);

        assertThat(extensions).containsExactlyInAnyOrder("postgis", "vector", "pgcrypto");
    }

    /** 验证 MyBatis-Plus 乐观锁会拒绝使用旧 version 的并发更新。 */
    @Test
    void staleVersionUpdateShouldAffectZeroRows() {
        CommunityEntity original = createCommunity("LOCK-" + UUID.randomUUID());
        CommunityEntity firstReader = communityMapper.selectById(original.getId());
        CommunityEntity staleReader = communityMapper.selectById(original.getId());

        firstReader.setCommunityName("第一次更新");
        int firstUpdatedRows = communityMapper.updateById(firstReader);
        staleReader.setCommunityName("过期更新");
        int staleUpdatedRows = communityMapper.updateById(staleReader);

        assertThat(firstUpdatedRows).isEqualTo(1);
        assertThat(staleUpdatedRows).isZero();
        CommunityEntity stored = communityMapper.selectById(original.getId());
        assertThat(stored.getCommunityName()).isEqualTo("第一次更新");
        assertThat(stored.getVersion()).isEqualTo(1L);
    }

    /** 验证分页拦截器生成 PostgreSQL 分页 SQL 并返回正确页元数据。 */
    @Test
    void paginationInterceptorShouldReturnRequestedPage() {
        createCommunity("PAGE-A-" + UUID.randomUUID());
        createCommunity("PAGE-B-" + UUID.randomUUID());

        Page<CommunityEntity> result = communityMapper.selectPage(new Page<>(1, 1), null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getTotal()).isGreaterThanOrEqualTo(2);
        assertThat(result.getPages()).isGreaterThanOrEqualTo(2);
    }

    /** 验证审计客户端 IP 能以 PostgreSQL INET 类型写入，而不会被 VARCHAR 参数拒绝。 */
    @Test
    void operationLogShouldPersistInetClientAddress() {
        ObjectMapper objectMapper = new ObjectMapper();
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setOperationType("PERSISTENCE_INET_TEST");
        entity.setResourceType("TEST");
        entity.setRequestId(UUID.randomUUID().toString());
        entity.setClientIp("127.0.0.1");
        entity.setOperationDetail(objectMapper.createObjectNode());
        entity.setSuccess(true);
        entity.setOperatedAt(OffsetDateTime.parse("2026-07-14T00:00:00+08:00"));
        entity.setBeforeData(objectMapper.createObjectNode());
        entity.setAfterData(objectMapper.createObjectNode());
        entity.setChangedFields(objectMapper.createArrayNode());

        int affectedRows = operationLogRepository.insert(entity);

        assertThat(affectedRows).isEqualTo(1);
        String storedAddress = jdbcTemplate.queryForObject(
                "SELECT host(client_ip) FROM audit.operation_log WHERE id = ?", String.class, entity.getId());
        assertThat(storedAddress).isEqualTo("127.0.0.1");
    }

    /** 验证 V18 只将旧版 RENEWAL CURRENT 结果标记为 STALE，新版 V1.1 结果保持 CURRENT。 */
    @Test
    void v18ShouldStaleOnlyLegacyRenewalCurrentResults() throws IOException {
        UUID legacyBuildingId = createBuilding("V18-OLD-" + UUID.randomUUID());
        UUID currentBuildingId = createBuilding("V18-V11-" + UUID.randomUUID());
        UUID legacyRenewalRuleId = ruleId("RENEWAL", "RENEWAL-V1");
        UUID currentRenewalRuleId = ruleId("RENEWAL", "RENEWAL-V1.1");
        UUID riskRuleId = ruleId("RISK", "RISK-V1");
        UUID legacyRiskId = insertRisk(legacyBuildingId, riskRuleId);
        UUID currentRiskId = insertRisk(currentBuildingId, riskRuleId);

        UUID legacyRenewalId = insertRenewal(legacyBuildingId, legacyRiskId, legacyRenewalRuleId, "ALL");
        UUID currentRenewalId = insertRenewal(currentBuildingId, currentRiskId, currentRenewalRuleId, "ALL");

        jdbcTemplate.execute(readMigrationSql("/db/migration/V18__stale_legacy_renewal_results_after_v1_1.sql"));

        assertThat(renewalStatus(legacyRenewalId)).isEqualTo("STALE");
        assertThat(renewalStaleReason(legacyRenewalId)).isEqualTo("RULE_CHANGED:RENEWAL-V1.1");
        assertThat(renewalStatus(currentRenewalId)).isEqualTo("CURRENT");
        assertThat(renewalStaleReason(currentRenewalId)).isNull();
    }

    private CommunityEntity createCommunity(String code) {
        CommunityEntity entity = new CommunityEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommunityCode(code);
        entity.setCommunityName("数据库平台测试小区");
        entity.setBuildingCount(0);
        entity.setHouseholdCount(0);
        entity.setResidentCount(0);
        entity.setStatus("ACTIVE");
        entity.setExtraAttributes(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode());
        entity.setVersion(0L);
        communityMapper.insert(entity);
        return entity;
    }

    private UUID createBuilding(String code) {
        CommunityEntity community = createCommunity("C-" + code);
        UUID buildingId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, 'V18 测试楼', 'V18 测试路 1 号', 1990, 'BRICK_CONCRETE',
                        6, 1200, 10, 30, 'ACTIVE', '{}'::jsonb)
                """, buildingId, community.getId(), "B-" + code);
        return buildingId;
    }

    private UUID ruleId(String ruleType, String versionCode) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM core.rule_version
                WHERE rule_type = ? AND version_code = ? AND deleted_at IS NULL
                """, UUID.class, ruleType, versionCode);
    }

    private UUID insertRisk(UUID buildingId, UUID ruleId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.risk_assessment
                  (id, assessment_code, building_id, assessment_version, rule_version_id,
                   risk_score, confidence_score, evidence_reliability_score, risk_level,
                   dimension_scores, score_explanation, input_snapshot, input_checksum,
                   recommendation, need_manual_review, need_professional_inspection, status,
                   calculation_batch_id, engine_version, trigger_type)
                VALUES (?, ?, ?, 'TEST-V1', ?, 60.00, 75.00, 80.00, 'HIGH',
                        '[]'::jsonb, '{}'::jsonb, '{}'::jsonb, ?, '人工复核',
                        true, true, 'CURRENT', ?, 'phase4-rule-engine-v1', 'MANUAL')
                """, id, "RISK-" + id.toString().substring(0, 8), buildingId, ruleId,
                checksum(id), UUID.randomUUID());
        return id;
    }

    private UUID insertRenewal(UUID buildingId, UUID riskId, UUID ruleId, String scopeKey) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO core.renewal_priority
                  (id, building_id, risk_assessment_id, rule_version_id, priority_version,
                   priority_score, priority_level, ranking, ranking_scope, ranking_scope_key,
                   factor_details, input_snapshot, input_checksum, recommendation, status,
                   calculation_batch_id, engine_version, trigger_type)
                VALUES (?, ?, ?, ?, 'TEST-V1', 70.00, 'P2', 1, '{"scopeType":"ALL"}'::jsonb,
                        ?, '{}'::jsonb, '{}'::jsonb, ?, '纳入更新评估', 'CURRENT',
                        ?, 'phase4-rule-engine-v1', 'MANUAL')
                """, id, buildingId, riskId, ruleId, scopeKey, checksum(id), UUID.randomUUID());
        return id;
    }

    private String renewalStatus(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM core.renewal_priority WHERE id = ?", String.class, id);
    }

    private String renewalStaleReason(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT stale_reason FROM core.renewal_priority WHERE id = ?", String.class, id);
    }

    private String checksum(UUID id) {
        return id.toString().replace("-", "") + id.toString().replace("-", "");
    }

    private String readMigrationSql(String resourcePath) throws IOException {
        try (var stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Migration not found: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
