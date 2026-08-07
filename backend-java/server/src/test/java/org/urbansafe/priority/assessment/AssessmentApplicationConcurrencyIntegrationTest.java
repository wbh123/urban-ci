package org.urbansafe.priority.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 使用真实 PostgreSQL 事务验证同一楼栋并发重算后的 CURRENT 唯一性。 */
class AssessmentApplicationConcurrencyIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AssessmentApplicationService service;

    @Test
    void concurrentForcedCalculationsLeaveOneCurrentResultPerAssessmentType() throws Exception {
        UUID buildingId = createBuilding();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<String, Object>> first = executor.submit(() -> calculateAfterBarrier(buildingId, ready, start));
            Future<Map<String, Object>> second = executor.submit(() -> calculateAfterBarrier(buildingId, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(first.get(60, TimeUnit.SECONDS)).containsEntry("reused", false);
            assertThat(second.get(60, TimeUnit.SECONDS)).containsEntry("reused", false);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(countCurrent("core.completeness_assessment", buildingId)).isEqualTo(1);
        assertThat(countCurrent("core.risk_assessment", buildingId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM core.renewal_priority
                WHERE building_id=? AND ranking_scope_key='ALL' AND status='CURRENT'
                """, Integer.class, buildingId)).isEqualTo(1);
        assertThat(countAll("core.completeness_assessment", buildingId)).isEqualTo(2);
        assertThat(countAll("core.risk_assessment", buildingId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM core.renewal_priority
                WHERE building_id=? AND ranking_scope_key='ALL'
                """, Integer.class, buildingId)).isEqualTo(2);
    }

    private Map<String, Object> calculateAfterBarrier(
            UUID buildingId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("并发测试启动屏障超时");
        }
        return service.calculate(buildingId, true, Set.of("ALL"), "MANUAL", null);
    }

    private UUID createBuilding() {
        UUID communityId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO core.community
                  (id, community_code, community_name, administrative_region, building_count,
                   household_count, resident_count, status, extra_attributes)
                VALUES (?, ?, ?, '并发测试区', 1, 20, 60, 'ACTIVE', '{}'::jsonb)
                """, communityId, "CONC-C-" + suffix, "并发评分测试小区-" + suffix);
        jdbcTemplate.update("""
                INSERT INTO core.building
                  (id, community_id, building_code, building_name, address, construction_year,
                   structure_type, floor_count, building_area, household_count, resident_count,
                   status, extra_attributes)
                VALUES (?, ?, ?, ?, '并发测试路 1 号', 1988, 'BRICK_CONCRETE', 7, 1800,
                        20, 60, 'ACTIVE', '{}'::jsonb)
                """, buildingId, communityId, "CONC-B-" + suffix, "并发评分测试楼-" + suffix);
        return buildingId;
    }

    private int countCurrent(String table, UUID buildingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE building_id=? AND status='CURRENT'",
                Integer.class, buildingId);
    }

    private int countAll(String table, UUID buildingId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE building_id=?",
                Integer.class, buildingId);
    }
}
