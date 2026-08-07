package org.urbansafe.priority.inspection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class InspectionStateRulesTest {

    @Test
    void phase2TaskStateGraphOnlyAllowsPlannedTransitions() {
        Set<String> transitions = Set.of(
                "PENDING->IN_PROGRESS",
                "PENDING->CANCELLED",
                "IN_PROGRESS->COMPLETED",
                "IN_PROGRESS->CANCELLED");

        assertThat(transitions)
                .contains("PENDING->IN_PROGRESS", "IN_PROGRESS->COMPLETED")
                .doesNotContain("PENDING->COMPLETED", "COMPLETED->IN_PROGRESS");
    }
}
