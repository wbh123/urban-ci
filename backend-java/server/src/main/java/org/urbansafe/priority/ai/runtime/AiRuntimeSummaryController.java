package org.urbansafe.priority.ai.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AiRuntimeApi;
import org.urbansafe.priority.model.dto.AiRuntimeSummarySuccessResponse;

@RestController
public class AiRuntimeSummaryController implements AiRuntimeApi {

    static final String CONSOLE_RUNTIME_ROLES =
            "hasAnyRole('EXPERT','PROFESSIONAL_REVIEWER','COMMUNITY_MANAGER','GOVERNMENT_MANAGER','ADMIN')";

    private final AiRuntimeSummaryService service;
    private final ObjectMapper objectMapper;

    public AiRuntimeSummaryController(AiRuntimeSummaryService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(CONSOLE_RUNTIME_ROLES)
    public ResponseEntity<AiRuntimeSummarySuccessResponse> getAiRuntimeSummary() {
        return ResponseEntity.ok(success(service.summary()));
    }

    private AiRuntimeSummarySuccessResponse success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", data);
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(body, AiRuntimeSummarySuccessResponse.class);
    }
}
