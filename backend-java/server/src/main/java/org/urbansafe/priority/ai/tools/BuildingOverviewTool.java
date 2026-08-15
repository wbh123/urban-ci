package org.urbansafe.priority.ai.tools;

import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.security.BusinessAccessService;

/** Spring AI 业务只读 Tool：楼栋基本档案概览。 */
@Component
public class BuildingOverviewTool {

    private final BuildingService buildingService;
    private final BusinessAccessService accessService;

    public BuildingOverviewTool(
            BuildingService buildingService,
            BusinessAccessService accessService) {
        this.buildingService = buildingService;
        this.accessService = accessService;
    }

    @Tool(description = """
            查询楼栋基本档案概览（名称、地址、结构、楼层、户数、档案完整度等）。
            在需要了解楼栋基础信息时使用。只读，不修改任何业务数据。
            """)
    public BuildingOverviewResult overview(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("BuildingOverviewTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadBuilding(id);
            BuildingDetailResult building = buildingService.getBuilding(id);
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new BuildingOverviewResult(
                    String.valueOf(building.id()),
                    building.buildingName(),
                    building.buildingCode(),
                    building.address(),
                    building.structureType(),
                    building.constructionYear(),
                    building.floorCount(),
                    building.householdCount(),
                    building.residentCount(),
                    building.archiveCompletenessScore());
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    public record BuildingOverviewResult(
            String buildingId,
            String buildingName,
            String buildingCode,
            String address,
            String structureType,
            Integer constructionYear,
            Integer floorCount,
            Integer householdCount,
            Integer residentCount,
            java.math.BigDecimal archiveCompletenessScore) {
    }
}
