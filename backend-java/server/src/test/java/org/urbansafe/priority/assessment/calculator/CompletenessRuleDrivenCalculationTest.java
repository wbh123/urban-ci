package org.urbansafe.priority.assessment.calculator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.assessment.AssessmentTestFixtures;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

class CompletenessRuleDrivenCalculationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompletenessCalculator calculator = new CompletenessCalculator();

    @Test
    void inspectionFullScoreWindowComesFromRule() throws Exception {
        BuildingAssessmentInput input = withInspections(AssessmentTestFixtures.fullInput(), List.of(
                new BuildingAssessmentInput.InspectionEvidence(
                        UUID.randomUUID(),
                        OffsetDateTime.of(2026, 5, 26, 10, 0, 0, 0, ZoneOffset.UTC),
                        "外墙", "MINOR", true, true, Map.of())));

        var lenient = calculator.calculate(input, ruleWith("inspectionRecency", mapper.readTree("""
                [
                  {"maxDays":180,"score":100},
                  {"maxDays":365,"score":80},
                  {"fallbackScore":20}
                ]
                """)));
        var strict = calculator.calculate(input, ruleWith("inspectionRecency", mapper.readTree("""
                [
                  {"maxDays":30,"score":100},
                  {"maxDays":365,"score":80},
                  {"fallbackScore":20}
                ]
                """)));

        assertThat(lenient.score()).isGreaterThan(strict.score());
        assertThat(strict.dimensions()).filteredOn(d -> d.code().equals("RECENT_INSPECTION"))
                .singleElement()
                .extracting(d -> d.score())
                .isEqualTo(new BigDecimal("80.00"));
    }

    @Test
    void imageFullScoreThresholdComesFromRule() throws Exception {
        BuildingAssessmentInput input = withImages(AssessmentTestFixtures.fullInput(), 2, List.of("外墙", "楼道"));

        var fourImageFull = calculator.calculate(input, ruleWith("imageCoverage", mapper.readTree("""
                [
                  {"minImages":4,"minParts":3,"score":100},
                  {"minImages":2,"minParts":2,"score":70},
                  {"minImages":1,"minParts":1,"score":40},
                  {"minImages":0,"minParts":0,"score":0}
                ]
                """)));
        var twoImageFull = calculator.calculate(input, ruleWith("imageCoverage", mapper.readTree("""
                [
                  {"minImages":2,"minParts":2,"score":100},
                  {"minImages":1,"minParts":1,"score":40},
                  {"minImages":0,"minParts":0,"score":0}
                ]
                """)));

        assertThat(twoImageFull.score()).isGreaterThan(fourImageFull.score());
        assertThat(twoImageFull.dimensions()).filteredOn(d -> d.code().equals("IMAGE_COVERAGE"))
                .singleElement()
                .extracting(d -> d.score())
                .isEqualTo(new BigDecimal("100.00"));
    }

    @Test
    void professionalInspectionValidYearsComesFromRule() throws Exception {
        BuildingAssessmentInput input = withBusinessEvidence(AssessmentTestFixtures.fullInput(), List.of(
                new BuildingAssessmentInput.BusinessEvidence(
                        UUID.randomUUID(), "MAINTENANCE_RECORD", "VERIFIED",
                        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                        new BigDecimal("40"), "MODERATE", Map.of()),
                new BuildingAssessmentInput.BusinessEvidence(
                        UUID.randomUUID(), "PROFESSIONAL_INSPECTION", "VERIFIED",
                        OffsetDateTime.of(2024, 7, 25, 0, 0, 0, 0, ZoneOffset.UTC),
                        new BigDecimal("75"), "SEVERE", Map.of())));

        var fiveYears = calculator.calculate(input, ruleWith("evidenceRecency", mapper.readTree("""
                {"maintenanceYears":5,"professionalInspectionYears":5}
                """)));
        var oneYear = calculator.calculate(input, ruleWith("evidenceRecency", mapper.readTree("""
                {"maintenanceYears":5,"professionalInspectionYears":1}
                """)));

        assertThat(fiveYears.score()).isGreaterThan(oneYear.score());
        assertThat(oneYear.dimensions()).filteredOn(d -> d.code().equals("PROFESSIONAL_INSPECTION"))
                .singleElement()
                .extracting(d -> d.score())
                .isEqualTo(new BigDecimal("80.00"));
    }

    private RuleSnapshot ruleWith(String field, JsonNode value) throws Exception {
        var root = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(baseRuleJson());
        root.set(field, value);
        return new RuleSnapshot(UUID.randomUUID(), "COMPLETENESS", "COMPLETENESS-V1.1",
                "完整度规则 V1.1", root, "checksum", "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC));
    }

    private BuildingAssessmentInput withInspections(
            BuildingAssessmentInput input,
            List<BuildingAssessmentInput.InspectionEvidence> inspections) {
        return new BuildingAssessmentInput(input.building(), input.community(), input.geometryAvailable(), inspections,
                input.availableImageCount(), input.imageParts(), input.businessEvidence(), input.residentReports(),
                input.eligibleAiEvidence(), input.excludedAiEvidence(), input.spatialMetrics(), input.calculationDate());
    }

    private BuildingAssessmentInput withImages(BuildingAssessmentInput input, int imageCount, List<String> parts) {
        return new BuildingAssessmentInput(input.building(), input.community(), input.geometryAvailable(), input.inspections(),
                imageCount, parts, input.businessEvidence(), input.residentReports(), input.eligibleAiEvidence(),
                input.excludedAiEvidence(), input.spatialMetrics(), input.calculationDate());
    }

    private BuildingAssessmentInput withBusinessEvidence(
            BuildingAssessmentInput input,
            List<BuildingAssessmentInput.BusinessEvidence> evidence) {
        return new BuildingAssessmentInput(input.building(), input.community(), input.geometryAvailable(), input.inspections(),
                input.availableImageCount(), input.imageParts(), evidence, input.residentReports(), input.eligibleAiEvidence(),
                input.excludedAiEvidence(), input.spatialMetrics(), input.calculationDate());
    }

    private String baseRuleJson() {
        return """
                {
                  "schemaVersion":"1.1",
                  "dimensions":[
                    {"code":"BASIC_ARCHIVE","label":"基础档案","weight":"0.35"},
                    {"code":"RECENT_INSPECTION","label":"近期巡检","weight":"0.25"},
                    {"code":"IMAGE_COVERAGE","label":"图片覆盖","weight":"0.15"},
                    {"code":"MAINTENANCE_RECORD","label":"维修资料","weight":"0.10"},
                    {"code":"PROFESSIONAL_INSPECTION","label":"专业检测","weight":"0.15"}
                  ],
                  "fieldWeights":{"constructionYear":20,"structureType":20,"floorCount":15,"buildingArea":10,"householdCount":10,"residentCount":10,"address":5,"geometry":10},
                  "inspectionRecency":[
                    {"maxDays":180,"score":100},
                    {"maxDays":365,"score":80},
                    {"maxDays":730,"score":50},
                    {"fallbackScore":20}
                  ],
                  "imageCoverage":[
                    {"minImages":4,"minParts":3,"score":100},
                    {"minImages":2,"minParts":2,"score":70},
                    {"minImages":1,"minParts":1,"score":40},
                    {"minImages":0,"minParts":0,"score":0}
                  ],
                  "evidenceCoverage":{"verifiedRecent":100,"verifiedOld":80,"unverified":40,"missing":0},
                  "evidenceRecency":{"maintenanceYears":5,"professionalInspectionYears":5},
                  "levels":[
                    {"code":"INSUFFICIENT","min":"0","maxExclusive":"50"},
                    {"code":"LIMITED","min":"50","maxExclusive":"70"},
                    {"code":"GOOD","min":"70","maxExclusive":"85"},
                    {"code":"EXCELLENT","min":"85","maxInclusive":"100"}
                  ]
                }
                """;
    }
}
