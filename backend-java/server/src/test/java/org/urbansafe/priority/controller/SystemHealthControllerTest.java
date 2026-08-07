package org.urbansafe.priority.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.model.dto.SystemHealthData;
import org.urbansafe.priority.model.dto.SystemHealthResponse;
import org.springframework.http.ResponseEntity;

class SystemHealthControllerTest {

    @AfterEach
    void clearRequestContext() {
        RequestContext.clear();
    }

    @Test
    void shouldReturnUnifiedSuccessResponse() {
        RequestContext.setRequestId("test-request-id");
        SystemHealthController controller = new SystemHealthController();

        ResponseEntity<SystemHealthResponse> responseEntity = controller.getSystemHealth();

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().getSuccess()).isTrue();
        assertThat(responseEntity.getBody().getRequestId()).isEqualTo("test-request-id");
        assertThat(responseEntity.getBody().getTimestamp())
                .isInstanceOf(OffsetDateTime.class);
        assertThat(responseEntity.getBody().getError()).isNull();
        assertThat(responseEntity.getBody().getData().getService())
                .isEqualTo("urban-safe-priority-server");
        assertThat(responseEntity.getBody().getData().getStatus())
                .isEqualTo(SystemHealthData.StatusEnum.UP);
    }
}
