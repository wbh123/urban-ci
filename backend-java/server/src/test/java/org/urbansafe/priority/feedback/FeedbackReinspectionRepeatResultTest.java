package org.urbansafe.priority.feedback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.feedback.repository.FeedbackClosureRepository;
import org.urbansafe.priority.feedback.repository.FeedbackRepository;
import org.urbansafe.priority.feedback.service.FeedbackClosureService;
import org.urbansafe.priority.feedback.service.FeedbackService;
import org.urbansafe.priority.inspection.service.Phase2InspectionService;

class FeedbackReinspectionRepeatResultTest {

    @Test
    void recordedResultCannotBeSubmittedAgain() {
        FeedbackRepository repository = mock(FeedbackRepository.class);
        FeedbackClosureRepository closureRepository = mock(FeedbackClosureRepository.class);
        FeedbackClosureService service = new FeedbackClosureService(
                repository,
                closureRepository,
                mock(Phase2InspectionService.class),
                mock(Phase2AssetService.class),
                mock(FeedbackService.class));
        UUID reportId = UUID.randomUUID();
        when(repository.lockReport(reportId)).thenReturn(Optional.of(Map.of(
                "reportId", reportId,
                "reportCode", "DEMO-FEEDBACK-REPEAT",
                "status", "RESOLVED",
                "buildingId", UUID.randomUUID())));
        when(closureRepository.latestReinspection(reportId)).thenReturn(Optional.of(Map.of(
                "taskId", UUID.randomUUID(),
                "taskCode", "IT-RECHECK-OLD",
                "status", "COMPLETED",
                "resultRecorded", true)));

        assertThatThrownBy(() -> service.completeReinspection(
                reportId, true, "重复提交旧任务复验结论。", UUID.randomUUID()))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("已提交复验结论");
        verify(repository).lockReport(reportId);
    }
}
