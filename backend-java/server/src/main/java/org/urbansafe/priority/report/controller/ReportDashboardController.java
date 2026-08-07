package org.urbansafe.priority.report.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.assessment.security.AssessmentAccessService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.ReportDashboardApi;
import org.urbansafe.priority.model.dto.RiskMapSuccessResponse;
import org.urbansafe.priority.model.dto.RiskOverviewSuccessResponse;
import org.urbansafe.priority.model.dto.RiskReportDetailSuccessResponse;
import org.urbansafe.priority.model.dto.RiskReportGenerationRequest;
import org.urbansafe.priority.model.dto.RiskReportGenerationSuccessResponse;
import org.urbansafe.priority.model.dto.RiskReportPageSuccessResponse;
import org.urbansafe.priority.model.dto.RiskReportPreviewSuccessResponse;

/** 第五阶段风险总览与楼栋报告入口。 */
@RestController
public class ReportDashboardController implements ReportDashboardApi {

    private final ReportDashboardService service;
    private final AssessmentAccessService accessService;
    private final ObjectMapper objectMapper;

    public ReportDashboardController(
            ReportDashboardService service,
            AssessmentAccessService accessService,
            ObjectMapper objectMapper) {
        this.service = service;
        this.accessService = accessService;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<RiskOverviewSuccessResponse> getRiskOverview(
            String scopeType, String scopeId) {
        return ResponseEntity.ok(success(
                service.overview(scopeType, scopeId), RiskOverviewSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<RiskMapSuccessResponse> getRiskMap(
            String scopeType, String scopeId) {
        return ResponseEntity.ok(success(
                service.riskMap(scopeType, scopeId), RiskMapSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<RiskReportPreviewSuccessResponse> previewBuildingRiskReport(
            UUID buildingId) {
        accessService.assertCanReadFull(buildingId);
        return ResponseEntity.ok(success(
                service.preview(buildingId), RiskReportPreviewSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.CALCULATE_ROLES)
    public ResponseEntity<RiskReportGenerationSuccessResponse> generateBuildingRiskReport(
            UUID buildingId, RiskReportGenerationRequest request) {
        accessService.assertCanCalculate(buildingId);
        boolean force = request != null && Boolean.TRUE.equals(request.getForce());
        return ResponseEntity.ok(success(
                service.generate(buildingId, force, CurrentUser.getUserId()),
                RiskReportGenerationSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.BATCH_AND_RANKING_ROLES)
    public ResponseEntity<RiskReportPageSuccessResponse> listRiskReports(
            UUID buildingId,
            UUID communityId,
            String status,
            Integer page,
            Integer size) {
        int safePage = page == null ? 0 : page;
        int safeSize = size == null ? 20 : size;
        return ResponseEntity.ok(success(
                service.list(buildingId, communityId, status, safePage, safeSize),
                RiskReportPageSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<RiskReportDetailSuccessResponse> getRiskReport(UUID reportId) {
        Map<String, Object> report = service.detail(reportId);
        accessService.assertCanReadFull((UUID) report.get("buildingId"));
        return ResponseEntity.ok(success(report, RiskReportDetailSuccessResponse.class));
    }

    @Override
    @PreAuthorize(AssessmentAccessService.FULL_READ_ROLES)
    public ResponseEntity<Resource> downloadRiskReport(UUID reportId) {
        ReportDownload download = service.download(reportId);
        accessService.assertCanReadFull(download.buildingId());
        ByteArrayResource resource = new ByteArrayResource(download.bytes()) {
            @Override
            public String getFilename() {
                return download.filename();
            }
        };
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentLength(download.bytes().length);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build());
        return ResponseEntity.ok().headers(headers).body(resource);
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
