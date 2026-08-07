package org.urbansafe.priority.assessment.rule;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.InvalidRequestException;

class AssessmentRuleTypeValidationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AssessmentRuleValidator validator = new AssessmentRuleValidator();

    @Test
    void acceptsLegalTypeSpecificRules() throws Exception {
        assertThatCode(() -> validator.validate("COMPLETENESS", mapper.readTree(completenessRule())))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("RISK", mapper.readTree(riskRule())))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate("RENEWAL", mapper.readTree(renewalRule())))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCompletenessRuleWithoutEvidenceRecency() throws Exception {
        ObjectNode rule = (ObjectNode) mapper.readTree(completenessRule());
        rule.remove("evidenceRecency");

        assertThatThrownBy(() -> validator.validate("COMPLETENESS", rule))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("evidenceRecency");
    }

    @Test
    void rejectsRiskRuleWithoutReviewedAiParameters() throws Exception {
        ObjectNode rule = (ObjectNode) mapper.readTree(riskRule());
        rule.remove("reviewedAi");

        assertThatThrownBy(() -> validator.validate("RISK", rule))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("reviewedAi");
    }

    @Test
    void rejectsRenewalRuleWithoutReliabilityFactor() throws Exception {
        ObjectNode rule = (ObjectNode) mapper.readTree(renewalRule());
        rule.remove("reliabilityFactor");

        assertThatThrownBy(() -> validator.validate("RENEWAL", rule))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("reliabilityFactor");
    }

    @Test
    void rejectsRenewalRuleWithUnknownRankingKey() throws Exception {
        ObjectNode rule = (ObjectNode) mapper.readTree(renewalRule());
        rule.putArray("rankingKeys").add("priorityScore:DESC").add("unknownScore:DESC");

        assertThatThrownBy(() -> validator.validate("RENEWAL", rule))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("rankingKeys");
    }

    private String completenessRule() {
        return """
                {
                  "dimensions":[
                    {"code":"BASIC_ARCHIVE","weight":"0.35"},
                    {"code":"RECENT_INSPECTION","weight":"0.25"},
                    {"code":"IMAGE_COVERAGE","weight":"0.15"},
                    {"code":"MAINTENANCE_RECORD","weight":"0.10"},
                    {"code":"PROFESSIONAL_INSPECTION","weight":"0.15"}
                  ],
                  "fieldWeights":{"constructionYear":20,"structureType":20,"floorCount":15,"buildingArea":10,"householdCount":10,"residentCount":10,"address":5,"geometry":10},
                  "inspectionRecency":[{"maxDays":180,"score":100},{"maxDays":365,"score":80},{"fallbackScore":20}],
                  "imageCoverage":[{"minImages":0,"minParts":0,"score":0},{"minImages":1,"minParts":1,"score":40},{"minImages":2,"minParts":2,"score":70},{"minImages":4,"minParts":3,"score":100}],
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

    private String riskRule() {
        return """
                {
                  "dimensions":[
                    {"code":"BUILDING_BASE","weight":"0.20"},
                    {"code":"INSPECTION_DEFECT","weight":"0.30"},
                    {"code":"PROFESSIONAL_HISTORY","weight":"0.20"},
                    {"code":"SPATIAL_ENVIRONMENT","weight":"0.10"},
                    {"code":"RESIDENT_FEEDBACK","weight":"0.10"},
                    {"code":"REVIEWED_AI","weight":"0.10"}
                  ],
                  "baseRisk":{"ageWeight":"0.60","structureWeight":"0.40","illegalModificationBonus":25,"groundFloorBusinessBonus":10},
                  "ageScores":[{"maxAge":20,"score":10},{"maxAge":999,"score":95},{"missing":true,"score":50}],
                  "structureScores":{"BRICK_CONCRETE":70,"UNKNOWN":50},
                  "severityScores":{"NONE":0,"MINOR":25,"MODERATE":50,"SEVERE":75,"CRITICAL":100},
                  "inspectionAggregation":{"maxSeverityWeight":"0.70","multiPartWeight":"0.20","persistenceWeight":"0.10"},
                  "reliability":{"PROFESSIONAL_VERIFIED":"1.00","AI_REVIEWED_REAL":"0.70","AI_MOCK":"0"},
                  "feedback":{"windowDays":365,"sameTypeThirtyDayCap":3,"scoreMultiplier":"20","urgencyWeights":{"LOW":"0.5","URGENT":"2.0"},"statusWeights":{"SUBMITTED":"1.0","CLOSED":"0.1"},"timeDecay":[{"maxDays":30,"factor":"1.0"}]},
                  "reviewedAi":{"quantityBonusPerExtraDefect":5,"maxQuantityBonus":20},
                  "confidence":{"completenessWeight":"0.70","evidenceReliabilityWeight":"0.30","lowThreshold":"60"},
                  "recommendations":{"manualReviewRiskThreshold":"50","lowConfidenceThreshold":"60","professionalInspectionRiskThreshold":"75"},
                  "levels":[
                    {"code":"LOW","min":"0","maxExclusive":"25"},
                    {"code":"MEDIUM","min":"25","maxExclusive":"50"},
                    {"code":"HIGH","min":"50","maxExclusive":"75"},
                    {"code":"VERY_HIGH","min":"75","maxInclusive":"100"}
                  ]
                }
                """;
    }

    private String renewalRule() {
        return """
                {
                  "dimensions":[
                    {"code":"RISK","weight":"0.45"},
                    {"code":"POPULATION_IMPACT","weight":"0.15"},
                    {"code":"BUILDING_AGE","weight":"0.10"},
                    {"code":"PUBLIC_VALUE","weight":"0.10"},
                    {"code":"FEEDBACK_URGENCY","weight":"0.10"},
                    {"code":"GOVERNANCE_URGENCY","weight":"0.10"}
                  ],
                  "populationImpact":{"residentWeight":"0.70","vulnerableWeight":"0.30","residentScores":[{"max":0,"score":0},{"max":2147483647,"score":100}],"vulnerableFullScoreCount":200},
                  "buildingAge":{"ageScores":[{"maxAge":20,"score":10},{"maxAge":999,"score":95},{"missing":true,"score":50}]},
                  "reliabilityFactor":{"base":"0.85","confidenceWeight":"0.15"},
                  "rankingKeys":["priorityScore:DESC","riskScore:DESC","confidenceScore:DESC","residentCount:DESC","buildingCode:ASC","buildingId:ASC"],
                  "levels":[
                    {"code":"P4","min":"0","maxExclusive":"40"},
                    {"code":"P3","min":"40","maxExclusive":"60"},
                    {"code":"P2","min":"60","maxExclusive":"80"},
                    {"code":"P1","min":"80","maxInclusive":"100"}
                  ]
                }
                """;
    }
}
