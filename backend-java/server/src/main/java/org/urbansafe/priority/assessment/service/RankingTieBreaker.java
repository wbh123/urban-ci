package org.urbansafe.priority.assessment.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 服务端正式排名的稳定比较器。 */
@Component
public class RankingTieBreaker {

    public Comparator<Map<String, Object>> comparator() {
        return Comparator
                .comparing((Map<String, Object> row) -> decimal(row.get("priorityScore")), Comparator.reverseOrder())
                .thenComparing(row -> decimal(row.get("riskScore")), Comparator.reverseOrder())
                .thenComparing(row -> decimal(row.get("confidenceScore")), Comparator.reverseOrder())
                .thenComparing(row -> integer(row.get("residentCount")), Comparator.reverseOrder())
                .thenComparing(row -> text(row.get("buildingCode")), Comparator.naturalOrder())
                .thenComparing(row -> uuid(row.get("buildingId")), Comparator.naturalOrder());
    }

    private BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private Integer integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value));
    }
}
