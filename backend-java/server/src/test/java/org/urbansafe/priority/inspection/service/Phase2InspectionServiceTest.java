package org.urbansafe.priority.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

class Phase2InspectionServiceTest {

    @Test
    void onsiteCompleteMovesInProgressTaskToOnsiteCompletedWhenRecordExists() {
        Phase2Repository repository = mock(Phase2Repository.class);
        Phase2InspectionService service = new Phase2InspectionService(repository);
        UUID taskId = UUID.randomUUID();
        when(repository.findTask(taskId))
                .thenReturn(Optional.of(Map.of("taskId", taskId, "status", "IN_PROGRESS")))
                .thenReturn(Optional.of(Map.of("taskId", taskId, "status", "ONSITE_COMPLETED")));
        when(repository.countRecords(taskId)).thenReturn(1);
        when(repository.transitionTask(taskId, "IN_PROGRESS", "ONSITE_COMPLETED")).thenReturn(1);

        Map<String, Object> result = service.onsiteComplete(taskId);

        assertThat(result.get("status")).isEqualTo("ONSITE_COMPLETED");
        verify(repository).transitionTask(taskId, "IN_PROGRESS", "ONSITE_COMPLETED");
    }

    @Test
    void onsiteCompleteRequiresAtLeastOneRecord() {
        Phase2Repository repository = mock(Phase2Repository.class);
        Phase2InspectionService service = new Phase2InspectionService(repository);
        UUID taskId = UUID.randomUUID();
        when(repository.findTask(taskId))
                .thenReturn(Optional.of(Map.of("taskId", taskId, "status", "IN_PROGRESS")));
        when(repository.countRecords(taskId)).thenReturn(0);

        assertThatThrownBy(() -> service.onsiteComplete(taskId))
                .isInstanceOf(ResourceConflictException.class);
        verify(repository, never()).transitionTask(taskId, "IN_PROGRESS", "ONSITE_COMPLETED");
    }

    @Test
    void finalCompleteOnlyMovesOnsiteCompletedTaskToCompleted() {
        Phase2Repository repository = mock(Phase2Repository.class);
        Phase2InspectionService service = new Phase2InspectionService(repository);
        UUID taskId = UUID.randomUUID();
        when(repository.findTask(taskId))
                .thenReturn(Optional.of(Map.of("taskId", taskId, "status", "ONSITE_COMPLETED")))
                .thenReturn(Optional.of(Map.of("taskId", taskId, "status", "COMPLETED")));
        when(repository.transitionTask(taskId, "ONSITE_COMPLETED", "COMPLETED")).thenReturn(1);

        Map<String, Object> result = service.complete(taskId);

        assertThat(result.get("status")).isEqualTo("COMPLETED");
        verify(repository).transitionTask(taskId, "ONSITE_COMPLETED", "COMPLETED");
        verify(repository, never()).transitionTask(taskId, "IN_PROGRESS", "COMPLETED");
    }
}
