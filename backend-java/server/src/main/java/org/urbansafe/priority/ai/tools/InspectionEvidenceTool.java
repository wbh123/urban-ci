package org.urbansafe.priority.ai.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.evidence.service.BuildingEvidenceService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

/** Spring AI 业务只读 Tool：楼栋巡检任务与证据概况。 */
@Component
public class InspectionEvidenceTool {

    private final Phase2InspectionService inspectionService;
    private final BuildingEvidenceService evidenceService;
    private final BusinessAccessService accessService;

    public InspectionEvidenceTool(
            Phase2InspectionService inspectionService,
            BuildingEvidenceService evidenceService,
            BusinessAccessService accessService) {
        this.inspectionService = inspectionService;
        this.evidenceService = evidenceService;
        this.accessService = accessService;
    }

    @Tool(name = "inspection_evidence_overview", description = """
            查询楼栋巡检任务、巡检记录与证据数量概况。
            在需要了解该楼栋是否有巡检历史、证据充分性时使用。只读，不修改任何业务数据。
            """)
    public InspectionOverviewResult overview(String buildingId) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("InspectionEvidenceTool", "SPRING_BOOT");
        try {
            UUID id = UUID.fromString(buildingId);
            accessService.assertCanReadBuilding(id);
            List<Map<String, Object>> tasks = inspectionService.listTasks(id, null);
            int recordCount = 0;
            for (Map<String, Object> task : tasks) {
                Object taskId = task.get("taskId");
                if (taskId != null) {
                    recordCount += inspectionService.listRecords(UUID.fromString(String.valueOf(taskId))).size();
                }
            }
            long evidenceCount = 0;
            try {
                var page = evidenceService.listBuildingEvidence(id, new ApiPageRequest(1, 1));
                evidenceCount = page.totalElements();
            } catch (RuntimeException ex) {
                evidenceCount = -1; // 证据服务不可用时标记为未知
            }
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new InspectionOverviewResult(
                    String.valueOf(id),
                    tasks.size(),
                    recordCount,
                    evidenceCount);
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    public record InspectionOverviewResult(
            String buildingId,
            int inspectionTaskCount,
            int inspectionRecordCount,
            long evidenceCount) {
    }
}
