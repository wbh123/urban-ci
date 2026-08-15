package org.urbansafe.priority.ai.dashboard;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AiDashboardApi;
import org.urbansafe.priority.model.dto.AiDashboardActivitySuccessResponse;
import org.urbansafe.priority.model.dto.AiDashboardBuildingsSuccessResponse;
import org.urbansafe.priority.model.dto.AiDashboardOverviewSuccessResponse;

@RestController
public class AiDashboardController implements AiDashboardApi {

    private final AiDashboardService service;
    private final ObjectMapper objectMapper;

    public AiDashboardController(AiDashboardService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<AiDashboardOverviewSuccessResponse> getAiDashboardOverview() {
        return ResponseEntity.ok(success(service.overview(), AiDashboardOverviewSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<AiDashboardActivitySuccessResponse> getAiDashboardActivity(Integer limit) {
        int safeLimit = limit == null ? 20 : limit;
        return ResponseEntity.ok(success(service.activity(safeLimit), AiDashboardActivitySuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<AiDashboardBuildingsSuccessResponse> getAiDashboardBuildings() {
        return ResponseEntity.ok(success(service.buildings(), AiDashboardBuildingsSuccessResponse.class));
    }

    private <T> T success(Object data, Class<T> responseType) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", metadata.success());
        result.put("data", data);
        result.put("error", null);
        result.put("requestId", metadata.requestId());
        result.put("timestamp", metadata.timestamp());
        return objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(result, responseType);
    }
}
