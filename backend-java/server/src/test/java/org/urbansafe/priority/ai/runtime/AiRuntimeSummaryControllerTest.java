package org.urbansafe.priority.ai.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.model.dto.AiRuntimeSummarySuccessResponse;

class AiRuntimeSummaryControllerTest {

    @Test
    void summaryKeepsStandardSuccessEnvelope() {
        AiRuntimeSummaryService service = mock(AiRuntimeSummaryService.class);
        when(service.summary()).thenReturn(Map.of(
                "generatedAt", "2026-08-14T05:00:00Z",
                "state", "READY",
                "services", List.of(),
                "policy", "Dify 优先 / 本地兜底"));
        AiRuntimeSummaryController controller = new AiRuntimeSummaryController(
                service, new ObjectMapper().findAndRegisterModules());

        ResponseEntity<AiRuntimeSummarySuccessResponse> response = controller.getAiRuntimeSummary();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Boolean.TRUE, response.getBody().getSuccess());
        assertNotNull(response.getBody().getData());
    }

    @Test
    void summaryIsReadableByConsoleRolesWithoutOpeningAdminGovernanceEndpoint() throws Exception {
        Method method = AiRuntimeSummaryController.class.getMethod("getAiRuntimeSummary");
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals(AiRuntimeSummaryController.CONSOLE_RUNTIME_ROLES, authorization.value());
    }
}
