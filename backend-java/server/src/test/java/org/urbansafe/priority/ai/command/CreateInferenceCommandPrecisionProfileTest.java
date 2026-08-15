package org.urbansafe.priority.ai.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateInferenceCommandPrecisionProfileTest {

    @Test
    void canonicalCommandShouldCarryPrecisionProfile() {
        CreateInferenceCommand command = new CreateInferenceCommand(
                UUID.randomUUID(), "REAL", "AI-VISION-LOCAL-001", "FAST_API",
                "VISION_INFERENCE", null, "PRECISION", "key", UUID.randomUUID());
        assertEquals("PRECISION", command.inferenceProfile());
    }

    @Test
    void legacyConstructorShouldKeepProfileAbsent() {
        CreateInferenceCommand command = new CreateInferenceCommand(
                UUID.randomUUID(), "MOCK", "AI-DEFECT-MOCK-001", "key", UUID.randomUUID());
        assertNull(command.inferenceProfile());
    }
}
