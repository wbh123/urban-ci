package org.urbansafe.priority.assessment.calculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.BusinessEvidence;

/**
 * 评估当前证据集合的可靠性。公众线索低于人工巡检，人工巡检低于专业检测；
 * MOCK、待复核和已排除人工智能结果不进入可靠性分。
 */
@Component
public class EvidenceReliabilityEvaluator {

    public BigDecimal evaluate(BuildingAssessmentInput input) {
        List<BigDecimal> values = new ArrayList<>();
        input.inspections().forEach(item -> values.add(new BigDecimal("80")));
        for (BusinessEvidence evidence : input.businessEvidence()) {
            values.add(reliability(evidence));
        }
        input.residentReports().forEach(item -> values.add(new BigDecimal("35")));
        input.eligibleAiEvidence().forEach(item -> values.add(new BigDecimal("70")));
        input.spatialMetrics().forEach(item -> values.add(new BigDecimal("60")));
        if (input.geometryAvailable()) {
            values.add(new BigDecimal("70"));
        }
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = sum.divide(BigDecimal.valueOf(values.size()), 6, java.math.RoundingMode.HALF_UP);

        boolean professional = input.businessEvidence().stream()
                .anyMatch(item -> "PROFESSIONAL_INSPECTION".equals(item.evidenceType())
                        && upper(item.reliabilityLevel()).contains("VERIFIED"));
        if (!professional && average.compareTo(new BigDecimal("75")) > 0) {
            average = new BigDecimal("75");
        }
        return AssessmentMath.output(average);
    }

    private BigDecimal reliability(BusinessEvidence evidence) {
        String level = upper(evidence.reliabilityLevel());
        if ("PROFESSIONAL_INSPECTION".equals(evidence.evidenceType()) && level.contains("VERIFIED")) {
            return new BigDecimal("100");
        }
        if ("MAINTENANCE_RECORD".equals(evidence.evidenceType()) && level.contains("VERIFIED")) {
            return new BigDecimal("85");
        }
        if (level.contains("VERIFIED") || level.contains("REVIEWED")) {
            return new BigDecimal("70");
        }
        return new BigDecimal("40");
    }

    private String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
