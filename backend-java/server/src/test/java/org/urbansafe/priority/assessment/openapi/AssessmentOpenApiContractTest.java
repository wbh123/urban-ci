package org.urbansafe.priority.assessment.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AssessmentOpenApiContractTest {

    @Test
    void assessmentContractUsesNamedResponsesAndContainsDisclaimer() throws IOException {
        String yaml;
        try (var stream = getClass().getResourceAsStream("/assessment/openapi-assessment.yaml")) {
            if (stream == null) {
                throw new IOException("assessment OpenAPI resource not found");
            }
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(yaml.contains("/api/v1/assessment-rules"));
        assertTrue(yaml.contains("/api/v1/assessments/buildings/{buildingId}/calculate"));
        assertTrue(yaml.contains("/api/v1/assessments/buildings/{buildingId}/summary"));
        assertTrue(yaml.contains("BuildingAssessmentSummarySuccessResponse"));
        assertTrue(yaml.contains("/api/v1/renewal-priorities"));
        assertTrue(yaml.contains("disclaimer"));
        assertFalse(yaml.contains("Get200Response"));
        assertFalse(yaml.contains("Post201Response"));
    }
}
