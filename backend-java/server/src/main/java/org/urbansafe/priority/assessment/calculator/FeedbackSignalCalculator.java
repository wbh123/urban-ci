package org.urbansafe.priority.assessment.calculator;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.FeedbackEvidence;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.rule.RuleAccess;

/** 公众反馈 V1 聚合器，不读取自由文本，不使用关键词或大语言模型判断严重度。 */
@Component
public class FeedbackSignalCalculator {

    public BigDecimal calculate(BuildingAssessmentInput input, RuleSnapshot rule) {
        RuleAccess access = new RuleAccess(rule);
        int cap = access.integer("/feedback/sameTypeThirtyDayCap", 3);
        BigDecimal multiplier = access.decimal("/feedback/scoreMultiplier", "20");
        Map<String, Integer> acceptedPerBucket = new HashMap<>();
        BigDecimal signalSum = BigDecimal.ZERO;

        List<FeedbackEvidence> ordered = input.residentReports().stream()
                .filter(item -> item.submittedAt() != null)
                .sorted(Comparator.comparing(FeedbackEvidence::submittedAt).reversed()
                        .thenComparing(item -> item.reportId().toString()))
                .toList();

        for (FeedbackEvidence report : ordered) {
            long days = Math.max(0, ChronoUnit.DAYS.between(
                    report.submittedAt().toLocalDate(), input.calculationDate()));
            BigDecimal statusWeight = access.mapDecimal(
                    "/feedback/statusWeights", upper(report.status(), "SUBMITTED"), "0");
            if (statusWeight.compareTo(BigDecimal.ZERO) == 0 || days > 365) {
                continue;
            }
            long bucket = days / 30;
            String key = upper(report.reportType(), "OTHER") + ":" + bucket;
            int count = acceptedPerBucket.getOrDefault(key, 0);
            if (count >= cap) {
                continue;
            }
            acceptedPerBucket.put(key, count + 1);
            BigDecimal urgencyWeight = access.mapDecimal(
                    "/feedback/urgencyWeights", upper(report.urgency(), "NORMAL"), "1.0");
            BigDecimal decay = timeDecay(days);
            signalSum = signalSum.add(urgencyWeight.multiply(statusWeight).multiply(decay));
        }
        return AssessmentMath.output(signalSum.multiply(multiplier));
    }

    private BigDecimal timeDecay(long days) {
        if (days <= 30) return new BigDecimal("1.0");
        if (days <= 90) return new BigDecimal("0.7");
        if (days <= 180) return new BigDecimal("0.4");
        if (days <= 365) return new BigDecimal("0.2");
        return BigDecimal.ZERO;
    }

    private String upper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase();
    }
}
