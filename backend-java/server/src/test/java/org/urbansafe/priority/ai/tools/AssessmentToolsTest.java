package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.assessment.service.AssessmentApplicationService;

@ExtendWith(MockitoExtension.class)
class AssessmentToolsTest {

    @Mock
    private AssessmentApplicationService assessmentService;

    @Mock
    private AssessmentAccessService accessService;

    @Test
    void riskSummaryShouldReadPublicAssessmentSummaryFieldNames() {
        UUID buildingId = UUID.randomUUID();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("buildingId", buildingId.toString());
        summary.put("buildingName", "测试楼栋");
        summary.put("communityName", "测试小区");
        summary.put("risk", Map.of(
                "riskLevel", "HIGH",
                "riskScore", 72.5));
        summary.put("completeness", Map.of("completenessScore", 88));
        summary.put("disclaimer", "仅供辅助研判");
        when(assessmentService.summary(buildingId)).thenReturn(summary);

        RiskAssessmentTool.RiskSummaryResult result =
                new RiskAssessmentTool(assessmentService, accessService).summary(buildingId.toString());

        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.riskScore()).isEqualTo("72.5");
        assertThat(result.completenessScore()).isEqualTo("88");
        verify(accessService).assertCanReadFull(buildingId);
    }

    @Test
    void renewalPriorityShouldReadCurrentRenewalsAndPreferAllScope() {
        UUID buildingId = UUID.randomUUID();
        Map<String, Object> current = new LinkedHashMap<>();
        current.put("buildingId", buildingId.toString());
        current.put("buildingName", "测试楼栋");
        current.put("renewalPriorities", List.of(
                Map.of(
                        "priorityLevel", "P2",
                        "priorityScore", 61.0,
                        "ranking", 3,
                        "rankingScopeKey", "COMMUNITY:" + UUID.randomUUID(),
                        "status", "CURRENT"),
                Map.of(
                        "priorityLevel", "P1",
                        "priorityScore", 78.0,
                        "ranking", 8,
                        "rankingScopeKey", "ALL",
                        "status", "CURRENT"),
                Map.of(
                        "priorityLevel", "P0",
                        "priorityScore", 95.0,
                        "ranking", 1,
                        "rankingScopeKey", "REGION:420100",
                        "status", "STALE")));
        current.put("disclaimer", "仅供辅助研判");
        when(assessmentService.current(buildingId)).thenReturn(current);

        RenewalPriorityTool.PriorityResult result =
                new RenewalPriorityTool(assessmentService, accessService).priority(buildingId.toString());

        assertThat(result.priorityLevel()).isEqualTo("P1");
        assertThat(result.priorityScore()).isEqualTo("78.0");
        assertThat(result.ranking()).isEqualTo("8");
        assertThat(result.rankingScopeKey()).isEqualTo("ALL");
        assertThat(result.status()).isEqualTo("CURRENT");
        verify(accessService).assertCanReadFull(buildingId);
    }
}
