package org.urbansafe.priority.assessment.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.model.dto.BuildingCurrentAssessmentResponse;
import org.urbansafe.priority.model.dto.RankingScopeType;

class AssessmentControllerContractTest {

    @Test
    void rankingScopeConversionAcceptsGeneratedSetType() throws Exception {
        AssessmentController controller = new AssessmentController(null, null, null, new ObjectMapper());
        Method method = AssessmentController.class.getDeclaredMethod("stringSet", Iterable.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> result = (Set<String>) method.invoke(
                controller,
                Set.of(RankingScopeType.ALL, RankingScopeType.COMMUNITY));

        assertEquals(Set.of("ALL", "COMMUNITY"), result);
    }
    @Test
    void controllerDtoConversionIgnoresInternalFields() throws Exception {
        AssessmentController controller = new AssessmentController(null, null, null, new ObjectMapper());
        Method method = AssessmentController.class.getDeclaredMethod("convert", Object.class, Class.class);
        method.setAccessible(true);
        UUID buildingId = UUID.randomUUID();

        BuildingCurrentAssessmentResponse result = (BuildingCurrentAssessmentResponse) method.invoke(
                controller,
                Map.of(
                        "buildingId", buildingId,
                        "buildingCode", "B-01",
                        "buildingName", "演示楼栋",
                        "communityId", UUID.randomUUID(),
                        "communityName", "演示小区",
                        "freshness", "CURRENT",
                        "completeness", Map.of(
                                "assessmentId", UUID.randomUUID(),
                                "completenessScore", 80,
                                "completenessLevel", "GOOD",
                                "status", "CURRENT",
                                "ruleVersion", "COMPLETENESS-V1",
                                "inputChecksum", "checksum",
                                "engineVersion", "phase4-rule-engine-v1",
                                "calculationBatchId", UUID.randomUUID()),
                        "renewalPriorities", java.util.List.of(),
                        "inputSummary", Map.of(),
                        "disclaimer", "仅用于风险筛查"),
                BuildingCurrentAssessmentResponse.class);

        assertEquals(buildingId, result.getBuildingId());
        assertNotNull(result.getCompleteness());
    }

}
