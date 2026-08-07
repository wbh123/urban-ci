package org.urbansafe.priority.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.RuleSnapshot;

public final class AssessmentTestFixtures {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private AssessmentTestFixtures() {
    }

    public static RuleSnapshot completenessRule() throws Exception {
        return rule("COMPLETENESS", "COMPLETENESS-V1", """
                {
                  "dimensions":[
                    {"code":"BASIC_ARCHIVE","label":"基础档案","weight":"0.35"},
                    {"code":"RECENT_INSPECTION","label":"近期巡检","weight":"0.25"},
                    {"code":"IMAGE_COVERAGE","label":"图片覆盖","weight":"0.15"},
                    {"code":"MAINTENANCE_RECORD","label":"维修资料","weight":"0.10"},
                    {"code":"PROFESSIONAL_INSPECTION","label":"专业检测","weight":"0.15"}
                  ],
                  "fieldWeights":{"constructionYear":20,"structureType":20,"floorCount":15,"buildingArea":10,"householdCount":10,"residentCount":10,"address":5,"geometry":10},
                  "levels":[
                    {"code":"INSUFFICIENT","min":"0","maxExclusive":"50"},
                    {"code":"LIMITED","min":"50","maxExclusive":"70"},
                    {"code":"GOOD","min":"70","maxExclusive":"85"},
                    {"code":"EXCELLENT","min":"85","maxInclusive":"100"}
                  ]
                }
                """);
    }

    public static RuleSnapshot riskRule() throws Exception {
        return rule("RISK", "RISK-V1", """
                {
                  "dimensions":[
                    {"code":"BUILDING_BASE","label":"楼龄与结构基础","weight":"0.20"},
                    {"code":"INSPECTION_DEFECT","label":"人工巡检病害","weight":"0.30"},
                    {"code":"PROFESSIONAL_HISTORY","label":"专业和历史证据","weight":"0.20"},
                    {"code":"SPATIAL_ENVIRONMENT","label":"空间与环境风险","weight":"0.10"},
                    {"code":"RESIDENT_FEEDBACK","label":"公众反馈","weight":"0.10"},
                    {"code":"REVIEWED_AI","label":"经复核人工智能证据","weight":"0.10"}
                  ],
                  "feedback":{
                    "sameTypeThirtyDayCap":3,
                    "scoreMultiplier":"20",
                    "urgencyWeights":{"LOW":"0.5","NORMAL":"1.0","HIGH":"1.5","URGENT":"2.0"},
                    "statusWeights":{"SUBMITTED":"1.0","ACCEPTED":"1.0","PROCESSING":"1.0","NEED_MORE_INFO":"0.8","RESOLVED":"0.3","CLOSED":"0.1","REJECTED":"0","CANCELLED":"0"}
                  },
                  "levels":[
                    {"code":"LOW","min":"0","maxExclusive":"25"},
                    {"code":"MEDIUM","min":"25","maxExclusive":"50"},
                    {"code":"HIGH","min":"50","maxExclusive":"75"},
                    {"code":"VERY_HIGH","min":"75","maxInclusive":"100"}
                  ]
                }
                """);
    }

    public static BuildingAssessmentInput fullInput() {
        UUID buildingId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID communityId = UUID.fromString("20000000-0000-0000-0000-000000000001");
        LocalDate date = LocalDate.of(2026, 7, 25);
        return new BuildingAssessmentInput(
                new BuildingAssessmentInput.BuildingSnapshot(
                        buildingId, communityId, "A-01", "一号楼", "示例路 1 号",
                        1966, "BRICK_CONCRETE", 7, new BigDecimal("5000"),
                        120, 360, 80, 50, false, false, Map.of()),
                new BuildingAssessmentInput.CommunitySnapshot(
                        communityId, "C-01", "示例小区", "示例区"),
                true,
                List.of(new BuildingAssessmentInput.InspectionEvidence(
                        UUID.randomUUID(),
                        OffsetDateTime.of(2026, 7, 20, 10, 0, 0, 0, ZoneOffset.UTC),
                        "外墙", "SEVERE", true, true,
                        Map.of("severity", "SEVERE"))),
                4,
                List.of("外墙", "楼道", "屋面"),
                List.of(
                        new BuildingAssessmentInput.BusinessEvidence(
                                UUID.randomUUID(), "MAINTENANCE_RECORD", "VERIFIED",
                                OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                                new BigDecimal("40"), "MODERATE", Map.of()),
                        new BuildingAssessmentInput.BusinessEvidence(
                                UUID.randomUUID(), "PROFESSIONAL_INSPECTION", "VERIFIED",
                                OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                                new BigDecimal("75"), "SEVERE", Map.of())),
                List.of(),
                List.of(new BuildingAssessmentInput.AiEvidence(
                        UUID.randomUUID(), "REAL", "SUCCEEDED", "CONFIRMED",
                        "ELIGIBLE", "WALL_CRACK", "SEVERE", 3, "外墙", null)),
                List.of(),
                List.of(new BuildingAssessmentInput.SpatialMetric(
                        "FLOOD_RISK", new BigDecimal("60"), null,
                        OffsetDateTime.now(ZoneOffset.UTC), null)),
                date);
    }

    public static BuildingAssessmentInput lowCompletenessSevereInput() {
        var full = fullInput();
        return new BuildingAssessmentInput(
                new BuildingAssessmentInput.BuildingSnapshot(
                        full.building().buildingId(), full.building().communityId(),
                        "A-02", "二号楼", null, 1950, "MASONRY",
                        null, null, null, null, null, null, true, false, Map.of()),
                full.community(),
                false,
                List.of(
                        full.inspections().getFirst(),
                        new BuildingAssessmentInput.InspectionEvidence(
                                UUID.randomUUID(),
                                OffsetDateTime.of(2026, 7, 19, 10, 0, 0, 0, ZoneOffset.UTC),
                                "楼道", "SEVERE", true, true,
                                Map.of("severity", "SEVERE"))),
                0,
                List.of(),
                List.of(),
                List.of(
                        new BuildingAssessmentInput.FeedbackEvidence(
                                UUID.randomUUID(), "WALL_CRACK", "URGENT", "SUBMITTED",
                                OffsetDateTime.of(2026, 7, 20, 0, 0, 0, 0, ZoneOffset.UTC)),
                        new BuildingAssessmentInput.FeedbackEvidence(
                                UUID.randomUUID(), "WALL_CRACK", "URGENT", "SUBMITTED",
                                OffsetDateTime.of(2026, 7, 18, 0, 0, 0, 0, ZoneOffset.UTC)),
                        new BuildingAssessmentInput.FeedbackEvidence(
                                UUID.randomUUID(), "WALL_CRACK", "URGENT", "SUBMITTED",
                                OffsetDateTime.of(2026, 7, 16, 0, 0, 0, 0, ZoneOffset.UTC))),
                List.of(),
                List.of(new BuildingAssessmentInput.AiEvidence(
                        UUID.randomUUID(), "MOCK", "SUCCEEDED", "UNREVIEWED",
                        "DEMO_ONLY", "WALL_CRACK", "CRITICAL", 5, "外墙",
                        "模拟结果仅用于演示")),
                List.of(),
                full.calculationDate());
    }

    private static RuleSnapshot rule(String type, String version, String json) throws Exception {
        return new RuleSnapshot(
                UUID.randomUUID(), type, version, version,
                MAPPER.readTree(json), "checksum", "ACTIVE", OffsetDateTime.now(ZoneOffset.UTC));
    }
}
