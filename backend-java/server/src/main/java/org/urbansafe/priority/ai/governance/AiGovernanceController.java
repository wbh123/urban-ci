package org.urbansafe.priority.ai.governance;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AiGovernanceApi;
import org.urbansafe.priority.model.dto.AiGovernanceStatusSuccessResponse;

/** 管理员人工智能运行状态与统计接口。 */
@RestController
public class AiGovernanceController implements AiGovernanceApi {

    private final AiProviderStatusService service;

    public AiGovernanceController(AiProviderStatusService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AiGovernanceStatusSuccessResponse> getAiGovernanceStatus() {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        AiGovernanceStatusSuccessResponse response = new AiGovernanceStatusSuccessResponse();
        response.setSuccess(metadata.success());
        response.setData(toDto(service.status()));
        response.setError(null);
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        return ResponseEntity.ok(response);
    }

    private static org.urbansafe.priority.model.dto.AiGovernanceStatus toDto(
            AiGovernanceStatus status) {
        org.urbansafe.priority.model.dto.AiGovernanceStatus dto =
                new org.urbansafe.priority.model.dto.AiGovernanceStatus();
        dto.setGeneratedAt(OffsetDateTime.ofInstant(status.generatedAt(), ZoneOffset.UTC));
        dto.setStatisticsWindow(status.statisticsWindow());
        dto.setProviders(status.providers().stream()
                .map(AiGovernanceController::toDto)
                .toList());
        dto.setTotal7d(toDto(status.total7d()));
        dto.setUnassignedLegacyTasks7d(status.unassignedLegacyTasks7d());
        dto.setHealthSemantics(status.healthSemantics());
        dto.setDisclaimer(status.disclaimer());
        return dto;
    }

    private static org.urbansafe.priority.model.dto.AiProviderStatus toDto(
            AiProviderStatus status) {
        org.urbansafe.priority.model.dto.AiProviderStatus dto =
                new org.urbansafe.priority.model.dto.AiProviderStatus();
        dto.setProviderCode(status.providerCode());
        dto.setEnabled(status.enabled());
        dto.setConfigured(status.configured());
        dto.setConfigurationStatus(status.configurationStatus());
        dto.setConnectivityStatus(status.connectivityStatus());
        dto.setCapabilities(status.capabilities());
        dto.setDefaultFor(status.defaultFor());
        dto.setMetrics7d(toDto(status.metrics7d()));
        return dto;
    }

    private static org.urbansafe.priority.model.dto.AiProviderMetrics toDto(
            AiProviderMetrics metrics) {
        org.urbansafe.priority.model.dto.AiProviderMetrics dto =
                new org.urbansafe.priority.model.dto.AiProviderMetrics();
        dto.setTotalTasks(metrics.totalTasks());
        dto.setSucceededTasks(metrics.succeededTasks());
        dto.setFailedTasks(metrics.failedTasks());
        dto.setReviewedTasks(metrics.reviewedTasks());
        dto.setPendingReviewTasks(metrics.pendingReviewTasks());
        dto.setAverageDurationMs(metrics.averageDurationMs());
        dto.setSuccessRate(metrics.successRate());
        return dto;
    }
}
