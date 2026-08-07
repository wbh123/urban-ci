package org.urbansafe.priority.assessment.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;
import org.urbansafe.priority.assessment.model.AssessmentResults.RiskResult;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

class RenewalPriorityCalculatorTest {

    private final RenewalPriorityCalculator calculator =
            new RenewalPriorityCalculator(new FeedbackSignalCalculator());

    @Test
    void priorityUsesConfidenceAsReliabilityFactorRatherThanRiskReplacement() throws Exception {
        RiskResult highConfidence = risk("80", "100");
        RiskResult lowConfidence = risk("80", "0");

        var high = calculator.calculate(AssessmentTestFixtures.fullInput(), highConfidence,
                renewalRule(), AssessmentTestFixtures.riskRule());
        var low = calculator.calculate(AssessmentTestFixtures.fullInput(), lowConfidence,
                renewalRule(), AssessmentTestFixtures.riskRule());

        assertThat(high.priorityScore()).isGreaterThan(low.priorityScore());
        assertThat(high.reliabilityFactor()).isEqualByComparingTo("1.000000");
        assertThat(low.reliabilityFactor()).isEqualByComparingTo("0.850000");
        assertThat(high.factors().stream().filter(item -> "RISK".equals(item.code()))
                .findFirst().orElseThrow().score()).isEqualByComparingTo("80.00");
    }


    @Test
    void publicValueUsesBuildingEvidenceRatherThanBuildingAttributes() throws Exception {
        var full = AssessmentTestFixtures.fullInput();
        var attributeOnly = withBuildingAndEvidence(full,
                new BuildingAssessmentInput.BuildingSnapshot(
                        full.building().buildingId(), full.building().communityId(),
                        full.building().buildingCode(), full.building().buildingName(), full.building().address(),
                        full.building().constructionYear(), full.building().structureType(), full.building().floorCount(),
                        full.building().buildingArea(), full.building().householdCount(), full.building().residentCount(),
                        full.building().elderlyCount(), full.building().childCount(), full.building().illegalModification(),
                        full.building().groundFloorBusiness(), Map.of("publicValueScore", 100)),
                List.of());
        var evidenceBacked = withBuildingAndEvidence(full, full.building(), List.of(
                new BuildingAssessmentInput.BusinessEvidence(
                        UUID.randomUUID(), "PUBLIC_VALUE", "VERIFIED",
                        OffsetDateTime.now(ZoneOffset.UTC), new BigDecimal("90"), null,
                        Map.of("score", "90"))));

        var attributeOnlyResult = calculator.calculate(attributeOnly, risk("0", "100"),
                renewalRule(), AssessmentTestFixtures.riskRule());
        var evidenceBackedResult = calculator.calculate(evidenceBacked, risk("0", "100"),
                renewalRule(), AssessmentTestFixtures.riskRule());

        assertThat(attributeOnlyResult.factors().stream().filter(item -> "PUBLIC_VALUE".equals(item.code()))
                .findFirst().orElseThrow().score()).isEqualByComparingTo("0.00");
        assertThat(evidenceBackedResult.factors().stream().filter(item -> "PUBLIC_VALUE".equals(item.code()))
                .findFirst().orElseThrow().score()).isEqualByComparingTo("90.00");
    }

    @Test
    void priorityThresholdsUseDocumentedBoundaries() throws Exception {
        var access = new org.urbansafe.priority.assessment.rule.RuleAccess(renewalRule());
        assertThat(access.level(new BigDecimal("39.99"))).isEqualTo("P4");
        assertThat(access.level(new BigDecimal("40.00"))).isEqualTo("P3");
        assertThat(access.level(new BigDecimal("60.00"))).isEqualTo("P2");
        assertThat(access.level(new BigDecimal("80.00"))).isEqualTo("P1");
    }


    private BuildingAssessmentInput withBuildingAndEvidence(
            BuildingAssessmentInput source,
            BuildingAssessmentInput.BuildingSnapshot building,
            List<BuildingAssessmentInput.BusinessEvidence> businessEvidence) {
        return new BuildingAssessmentInput(building, source.community(), source.geometryAvailable(),
                source.inspections(), source.availableImageCount(), source.imageParts(), businessEvidence,
                source.residentReports(), source.eligibleAiEvidence(), source.excludedAiEvidence(),
                source.spatialMetrics(), source.calculationDate());
    }

    private RiskResult risk(String score, String confidence) {
        return new RiskResult(new BigDecimal(score), "VERY_HIGH", new BigDecimal("80"),
                new BigDecimal(confidence), "HIGH", List.of(), List.of(), List.of(), List.of(),
                List.of(), true, true);
    }

    private RuleSnapshot renewalRule() throws Exception {
        return new RuleSnapshot(UUID.randomUUID(), "RENEWAL", "RENEWAL-V1", "RENEWAL-V1",
                AssessmentTestFixtures.MAPPER.readTree("""
                        {
                          "dimensions":[
                            {"code":"RISK","label":"安全风险","weight":"0.45"},
                            {"code":"POPULATION_IMPACT","label":"人口影响","weight":"0.15"},
                            {"code":"BUILDING_AGE","label":"楼龄","weight":"0.10"},
                            {"code":"PUBLIC_VALUE","label":"公共价值","weight":"0.10"},
                            {"code":"FEEDBACK_URGENCY","label":"反馈紧迫性","weight":"0.10"},
                            {"code":"GOVERNANCE_URGENCY","label":"治理紧迫性","weight":"0.10"}
                          ],
                          "levels":[
                            {"code":"P4","min":"0","maxExclusive":"40"},
                            {"code":"P3","min":"40","maxExclusive":"60"},
                            {"code":"P2","min":"60","maxExclusive":"80"},
                            {"code":"P1","min":"80","maxInclusive":"100"}
                          ]
                        }
                        """), "checksum", "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC));
    }
}
