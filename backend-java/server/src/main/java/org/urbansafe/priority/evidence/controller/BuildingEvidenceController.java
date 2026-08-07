package org.urbansafe.priority.evidence.controller;

import java.util.UUID;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.evidence.service.BuildingEvidenceService;
import org.urbansafe.priority.evidence.converter.EvidenceConverter;
import org.urbansafe.priority.evidence.result.EvidenceDetailResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.model.api.BuildingEvidenceApi;
import org.urbansafe.priority.model.dto.EvidenceSuccessResponse;
import org.urbansafe.priority.model.dto.CreateEvidenceRequest;
import org.urbansafe.priority.model.dto.DeletionSuccessResponse;
import org.urbansafe.priority.model.dto.DeletionResponse;
import org.urbansafe.priority.model.dto.EvidencePageResponse;
import org.urbansafe.priority.model.dto.EvidenceResponse;
import org.urbansafe.priority.model.dto.EvidencePageSuccessResponse;
import org.urbansafe.priority.model.dto.UpdateEvidenceRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildingEvidenceController implements BuildingEvidenceApi {

    private final BuildingEvidenceService buildingEvidenceService;
    private final EvidenceConverter evidenceConverter;

    public BuildingEvidenceController(BuildingEvidenceService buildingEvidenceService, EvidenceConverter evidenceConverter) {
        this.buildingEvidenceService = buildingEvidenceService;
        this.evidenceConverter = evidenceConverter;
    }

    @Override
    public ResponseEntity<EvidenceSuccessResponse> createBuildingEvidence(
            UUID buildingId, CreateEvidenceRequest createEvidenceRequest) {
        EvidenceDetailResult data = buildingEvidenceService.createBuildingEvidence(buildingId, evidenceConverter.toCommand(createEvidenceRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(wrap201(evidenceConverter.toResponse(data)));
    }

    @Override
    public ResponseEntity<EvidencePageSuccessResponse> listBuildingEvidence(
            UUID buildingId, Integer page, Integer size) {
        PageResult<EvidenceDetailResult> result = buildingEvidenceService.listBuildingEvidence(buildingId, ApiPageRequest.of(page, size));
        EvidencePageResponse data = new EvidencePageResponse(); data.setContent(result.content().stream().map(evidenceConverter::toResponse).toList()); data.setPage(new org.urbansafe.priority.model.dto.PageMetadata(result.page(), result.size(), result.totalElements(), Math.toIntExact(result.totalPages())));
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        EvidencePageSuccessResponse response = new EvidencePageSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EvidenceSuccessResponse> getBuildingEvidence(UUID evidenceId) {
        EvidenceDetailResult result = buildingEvidenceService.getBuildingEvidence(evidenceId);
        EvidenceResponse data = evidenceConverter.toResponse(result);
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        EvidenceSuccessResponse response = new EvidenceSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<EvidenceSuccessResponse> updateBuildingEvidence(
            UUID evidenceId, UpdateEvidenceRequest updateEvidenceRequest) {
        EvidenceDetailResult result = buildingEvidenceService.updateBuildingEvidence(evidenceId, evidenceConverter.toCommand(updateEvidenceRequest));
        EvidenceResponse data = evidenceConverter.toResponse(result);
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        EvidenceSuccessResponse response = new EvidenceSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<DeletionSuccessResponse> deleteBuildingEvidence(UUID evidenceId) {
        buildingEvidenceService.deleteBuildingEvidence(evidenceId);
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        DeletionSuccessResponse response = new DeletionSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);

        DeletionResponse data = new DeletionResponse(); data.setResourceId(evidenceId); data.setResourceType("BuildingEvidence"); data.setDeletedAt(metadata.timestamp()); response.setData(data);

        return ResponseEntity.ok(response);
    }

    private EvidenceSuccessResponse wrap201(EvidenceResponse data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        EvidenceSuccessResponse response = new EvidenceSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return response;
    }
}
