package org.urbansafe.priority.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankingTieBreakerTest {

    private final RankingTieBreaker tieBreaker = new RankingTieBreaker();

    @Test
    void equalPriorityUsesRiskConfidencePopulationCodeAndIdInOrder() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row(secondId, "B-02", 80, 70, 80, 500),
                row(firstId, "A-01", 80, 70, 80, 500),
                row(UUID.randomUUID(), "C-01", 80, 75, 60, 100),
                row(UUID.randomUUID(), "D-01", 81, 10, 10, 10)));

        rows.sort(tieBreaker.comparator());

        assertThat(rows.get(0).get("priorityScore")).isEqualTo(new BigDecimal("81"));
        assertThat(rows.get(1).get("riskScore")).isEqualTo(new BigDecimal("75"));
        assertThat(rows.get(2).get("buildingId")).isEqualTo(firstId);
        assertThat(rows.get(3).get("buildingId")).isEqualTo(secondId);
    }

    private Map<String, Object> row(
            UUID id, String code, int priority, int risk, int confidence, int residents) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("buildingId", id);
        row.put("buildingCode", code);
        row.put("priorityScore", BigDecimal.valueOf(priority));
        row.put("riskScore", BigDecimal.valueOf(risk));
        row.put("confidenceScore", BigDecimal.valueOf(confidence));
        row.put("residentCount", residents);
        return row;
    }
}
