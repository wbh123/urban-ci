package org.urbansafe.priority.spatial.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.model.api.SpatialApi;
import org.urbansafe.priority.model.dto.SpatialBoundaryResponse;
import org.urbansafe.priority.model.dto.SpatialBoundaryRevisionListSuccessResponse;
import org.urbansafe.priority.model.dto.SpatialBoundaryRevisionResponse;
import org.urbansafe.priority.model.dto.SpatialBoundarySuccessResponse;
import org.urbansafe.priority.model.dto.SpatialBoundaryUpsertRequest;
import org.urbansafe.priority.model.dto.SpatialBoundaryVerifyRequest;
import org.urbansafe.priority.model.dto.SpatialFeatureCollection;
import org.urbansafe.priority.model.dto.SpatialFeatureCollectionSuccessResponse;
import org.urbansafe.priority.spatial.service.SpatialBoundaryService;

/** 空间边界与地图视野查询的契约实现。 */
@RestController
public class SpatialController implements SpatialApi {

    private final SpatialBoundaryService service;
    private final ObjectMapper objectMapper;

    public SpatialController(SpatialBoundaryService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> getCommunityBoundary(UUID communityId) {
        return boundaryResponse(service.getCommunityBoundary(communityId));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> upsertCommunityBoundary(
            UUID communityId, SpatialBoundaryUpsertRequest request) {
        return boundaryResponse(service.upsertCommunityBoundary(communityId, toMap(request)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> verifyCommunityBoundary(
            UUID communityId, SpatialBoundaryVerifyRequest request) {
        return boundaryResponse(service.verifyCommunityBoundary(
                communityId, requiredVersion(request), request.getRemark()));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundaryRevisionListSuccessResponse>
            listCommunityBoundaryRevisions(UUID communityId) {
        return revisionResponse(service.listCommunityBoundaryRevisions(communityId));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> getBuildingBoundary(UUID buildingId) {
        return boundaryResponse(service.getBuildingBoundary(buildingId));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> upsertBuildingBoundary(
            UUID buildingId, SpatialBoundaryUpsertRequest request) {
        return boundaryResponse(service.upsertBuildingBoundary(buildingId, toMap(request)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> verifyBuildingBoundary(
            UUID buildingId, SpatialBoundaryVerifyRequest request) {
        return boundaryResponse(service.verifyBuildingBoundary(
                buildingId, requiredVersion(request), request.getRemark()));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundaryRevisionListSuccessResponse>
            listBuildingBoundaryRevisions(UUID buildingId) {
        return revisionResponse(service.listBuildingBoundaryRevisions(buildingId));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialFeatureCollectionSuccessResponse> listCommunityFeatures(
            Double west, Double south, Double east, Double north, Integer zoom) {
        requireViewport(west, south, east, north, zoom);
        return featureResponse(service.listCommunityFeatures(west, south, east, north, zoom));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialFeatureCollectionSuccessResponse> listBuildingFeatures(
            Double west,
            Double south,
            Double east,
            Double north,
            Integer zoom,
            UUID communityId) {
        requireViewport(west, south, east, north, zoom);
        return featureResponse(service.listBuildingFeatures(
                west, south, east, north, zoom, communityId));
    }

    private ResponseEntity<SpatialBoundarySuccessResponse> boundaryResponse(
            Map<String, Object> data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SpatialBoundarySuccessResponse response = new SpatialBoundarySuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);
        response.setData(objectMapper.convertValue(data, SpatialBoundaryResponse.class));
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<SpatialBoundaryRevisionListSuccessResponse> revisionResponse(
            List<Map<String, Object>> data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SpatialBoundaryRevisionListSuccessResponse response =
                new SpatialBoundaryRevisionListSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);
        response.setData(data.stream()
                .map(item -> objectMapper.convertValue(item, SpatialBoundaryRevisionResponse.class))
                .toList());
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<SpatialFeatureCollectionSuccessResponse> featureResponse(
            Map<String, Object> data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SpatialFeatureCollectionSuccessResponse response =
                new SpatialFeatureCollectionSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);
        response.setData(objectMapper.convertValue(data, SpatialFeatureCollection.class));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> toMap(SpatialBoundaryUpsertRequest request) {
        return objectMapper.convertValue(request, new TypeReference<>() { });
    }

    private int requiredVersion(SpatialBoundaryVerifyRequest request) {
        if (request == null || request.getExpectedVersion() == null) {
            throw new InvalidRequestException(
                    "SPATIAL_VERSION_REQUIRED", "expectedVersion 不能为空");
        }
        return request.getExpectedVersion();
    }

    private void requireViewport(
            Double west, Double south, Double east, Double north, Integer zoom) {
        if (west == null || south == null || east == null || north == null || zoom == null) {
            throw new InvalidRequestException(
                    "SPATIAL_VIEWPORT_REQUIRED", "west/south/east/north/zoom 均不能为空");
        }
    }
}
