package org.urbansafe.priority.assessment.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AssessmentControllerPermissionTest {

    @Test
    void writeEndpointsKeepMethodLevelAuthorization() {
        assertContainsRole("createAssessmentRule", "ADMIN");
        assertContainsRole("activateAssessmentRule", "ADMIN");
        assertContainsRole("calculateBuildingAssessment", "COMMUNITY_MANAGER");
        assertContainsRole("calculateBuildingAssessment", "GOVERNMENT_MANAGER");
        assertContainsRole("recalculateAssessments", "GOVERNMENT_MANAGER");
        assertContainsRole("getBuildingAssessmentSummary", "PROPERTY_INSPECTOR");
        assertContainsRole("getBuildingAssessmentSummary", "DISPOSAL_OPERATOR");
        assertContainsRole("getCurrentBuildingAssessment", "PROFESSIONAL_REVIEWER");
        assertDoesNotContainRole("getCurrentBuildingAssessment", "PROPERTY_INSPECTOR");
        assertDoesNotContainRole("listRenewalPriorities", "COMMUNITY_MANAGER");
    }

    private void assertContainsRole(String methodName, String role) {
        Method method = Arrays.stream(AssessmentController.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation != null && annotation.value().contains(role),
                () -> methodName + " must authorize " + role);
    }

    private void assertDoesNotContainRole(String methodName, String role) {
        Method method = Arrays.stream(AssessmentController.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertTrue(annotation != null && !annotation.value().contains(role),
                () -> methodName + " must not authorize " + role);
    }
}
