package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AiAutomationSettingsApi;
import org.urbansafe.priority.model.dto.AiAutomationSettingsSuccessResponse;
import org.urbansafe.priority.model.dto.AiAutomationSettingsUpdateRequest;
import org.urbansafe.priority.model.dto.AiAutomationSettingsView;

/** 管理员人工智能自动化设置接口。 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AiAutomationSettingsController implements AiAutomationSettingsApi {

    private final AiAutomationSettingsService service;

    public AiAutomationSettingsController(AiAutomationSettingsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<AiAutomationSettingsSuccessResponse> getAiAutomationSettings() {
        return ResponseEntity.ok(success(service.get()));
    }

    @Override
    public ResponseEntity<AiAutomationSettingsSuccessResponse> updateAiAutomationSettings(
            AiAutomationSettingsUpdateRequest request) {
        return ResponseEntity.ok(success(service.update(
                request.getAutoInferenceOnUpload(),
                request.getIntelligentWorkflowEnabled(),
                request.getKnowledgeQaEnabled(),
                request.getModelId(),
                CurrentUser.getUserId())));
    }

    private static AiAutomationSettingsSuccessResponse success(AiAutomationSettings settings) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        AiAutomationSettingsSuccessResponse response = new AiAutomationSettingsSuccessResponse();
        response.setSuccess(metadata.success());
        response.setData(toDto(settings));
        response.setError(null);
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        return response;
    }

    private static AiAutomationSettingsView toDto(AiAutomationSettings settings) {
        AiAutomationSettingsView dto = new AiAutomationSettingsView();
        dto.setAutoInferenceOnUpload(settings.autoInferenceOnUpload());
        dto.setIntelligentWorkflowEnabled(settings.intelligentWorkflowEnabled());
        dto.setKnowledgeQaEnabled(settings.knowledgeQaEnabled());
        dto.setModelId(settings.modelId());
        dto.setProviderCode(settings.providerCode());
        dto.setCapabilityType(settings.capabilityType());
        OffsetDateTime updatedAt = settings.updatedAt();
        dto.setUpdatedAt(updatedAt);
        return dto;
    }
}
