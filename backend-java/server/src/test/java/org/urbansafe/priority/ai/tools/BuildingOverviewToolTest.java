package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.execution.AiAgentExecution;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.security.BusinessAccessService;

/** 业务只读 Tool 测试：权限门禁 + 成功返回结构化结果。 */
class BuildingOverviewToolTest {

    private AiAgentExecution execution;

    @BeforeEach
    void beginTrace() {
        execution = new AiAgentExecution(
                UUID.randomUUID(), "BUILDING", UUID.randomUUID(), "分析", UUID.randomUUID(), "t");
        AiAgentTrace.begin(execution);
    }

    @AfterEach
    void endTrace() {
        AiAgentTrace.end();
    }

    @Test
    void returnsBuildingOverviewWithinPermission() {
        UUID buildingId = UUID.randomUUID();
        BuildingService buildingService = mock(BuildingService.class);
        when(buildingService.getBuilding(buildingId)).thenReturn(new BuildingDetailResult(
                buildingId, UUID.randomUUID(), "B-001", "一号楼", "测试地址", 2005, "BRICK",
                6, new BigDecimal("1200.0"), 60, 180, 20, 8, true, false, true,
                new BigDecimal("0.82"), "ACTIVE", null, "备注", null, null, 1L));
        BusinessAccessService accessService = mock(BusinessAccessService.class);

        BuildingOverviewTool tool = new BuildingOverviewTool(buildingService, accessService);
        BuildingOverviewTool.BuildingOverviewResult result = tool.overview(buildingId.toString());

        assertThat(result.buildingName()).isEqualTo("一号楼");
        assertThat(result.archiveCompletenessScore()).isEqualTo(new BigDecimal("0.82"));
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("BuildingOverviewTool")
                        && step.status() == AiAgentStepStatus.SUCCEEDED);
    }

    @Test
    void permissionDeniedPropagatesAndRecordsFailedStep() {
        UUID buildingId = UUID.randomUUID();
        BuildingService buildingService = mock(BuildingService.class);
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        doThrow(new RuntimeException("无权访问该楼栋")).when(accessService).assertCanReadBuilding(buildingId);

        BuildingOverviewTool tool = new BuildingOverviewTool(buildingService, accessService);
        assertThatThrownBy(() -> tool.overview(buildingId.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无权访问");
        assertThat(execution.steps()).anyMatch(step ->
                step.toolName().equals("BuildingOverviewTool")
                        && step.status() == AiAgentStepStatus.FAILED);
    }
}
