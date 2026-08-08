package org.urbansafe.priority.spatial.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.model.api.SpatialApi;
import org.urbansafe.priority.model.dto.SpatialBoundaryReviewRequest;
import org.urbansafe.priority.model.dto.SpatialBoundarySuccessResponse;
import org.urbansafe.priority.model.dto.SpatialBoundaryView;
import org.urbansafe.priority.model.dto.SpatialBoundaryWriteRequest;
import org.urbansafe.priority.model.dto.SpatialFeatureCollectionSuccessResponse;
import org.urbansafe.priority.model.dto.SpatialFeatureProperties;
import org.urbansafe.priority.model.dto.SpatialGeoJsonFeature;
import org.urbansafe.priority.model.dto.SpatialGeoJsonFeatureCollection;
import org.urbansafe.priority.spatial.SpatialBoundaryService;
import org.urbansafe.priority.spatial.SpatialBoundarySnapshot;
import org.urbansafe.priority.spatial.SpatialBoundaryWriteCommand;
import org.urbansafe.priority.spatial.SpatialGeoJsonService;
import org.urbansafe.priority.spatial.SpatialMapFeature;

/** R2 空间边界 HTTP 入口。地图渲染本身属于 R3，本控制器只提供边界生命周期与 GeoJSON 数据。 */
@RestController
public class SpatialController implements SpatialApi {

    private static final TypeReference<Map<String, Object>> GEO_JSON_OBJECT = new TypeReference<>() {};

    private final SpatialBoundaryService boundaryService;
    private final SpatialGeoJsonService geoJsonService;
    private final ObjectMapper objectMapper;

    public SpatialController(
            SpatialBoundaryService boundaryService,
            SpatialGeoJsonService geoJsonService,
            ObjectMapper objectMapper) {
        this.boundaryService = boundaryService;
        this.geoJsonService = geoJsonService;
        this.objectMapper = objectMapper;
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialFeatureCollectionSuccessResponse> querySpatialCommunities(
            Double west, Double south, Double east, Double north, Integer zoom) {
        return ResponseEntity.ok(featureCollectionResponse(
                geoJsonService.queryCommunities(west, south, east, north, zoom)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialFeatureCollectionSuccessResponse> querySpatialBuildings(
            Double west,
            Double south,
            Double east,
            Double north,
            Integer zoom,
            UUID communityId) {
        return ResponseEntity.ok(featureCollectionResponse(
                geoJsonService.queryBuildings(west, south, east, north, zoom, communityId)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> getCommunityBoundary(UUID communityId) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.getCommunity(communityId)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> upsertCommunityBoundary(
            UUID communityId, SpatialBoundaryWriteRequest request) {
        return ResponseEntity.ok(boundaryResponse(
                boundaryService.upsertCommunity(communityId, toCommand(request))));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> verifyCommunityBoundary(
            UUID communityId, SpatialBoundaryReviewRequest request) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.verifyCommunity(
                communityId, request.getExpectedVersion(), request.getRemark())));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> rejectCommunityBoundary(
            UUID communityId, SpatialBoundaryReviewRequest request) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.rejectCommunity(
                communityId, request.getExpectedVersion(), request.getRemark())));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> getBuildingBoundary(UUID buildingId) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.getBuilding(buildingId)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> upsertBuildingBoundary(
            UUID buildingId, SpatialBoundaryWriteRequest request) {
        return ResponseEntity.ok(boundaryResponse(
                boundaryService.upsertBuilding(buildingId, toCommand(request))));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> verifyBuildingBoundary(
            UUID buildingId, SpatialBoundaryReviewRequest request) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.verifyBuilding(
                buildingId, request.getExpectedVersion(), request.getRemark())));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<SpatialBoundarySuccessResponse> rejectBuildingBoundary(
            UUID buildingId, SpatialBoundaryReviewRequest request) {
        return ResponseEntity.ok(boundaryResponse(boundaryService.rejectBuilding(
                buildingId, request.getExpectedVersion(), request.getRemark())));
    }

    private SpatialBoundaryWriteCommand toCommand(SpatialBoundaryWriteRequest request) {
        return new SpatialBoundaryWriteCommand(
                request.getExpectedVersion(),
                request.getSourceType(),
                request.getSourceProvider(),
                request.getSourceObjectId(),
                request.getSourceCoordinateSystem(),
                writeJson(request.getSourceGeometry()),
                request.getDisplayCoordinateSystem(),
                writeJson(request.getDisplayGeometry()),
                request.getRemark());
    }

    private SpatialBoundarySuccessResponse boundaryResponse(SpatialBoundarySnapshot snapshot) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SpatialBoundarySuccessResponse response = new SpatialBoundarySuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(toView(snapshot));
        return response;
    }

    private SpatialBoundaryView toView(SpatialBoundarySnapshot snapshot) {
        SpatialBoundaryView view = new SpatialBoundaryView();
        view.setId(snapshot.id());
        view.setEntityType(snapshot.entityType().name());
        view.setEntityId(snapshot.entityId());
        view.setSourceType(snapshot.sourceType());
        view.setSourceProvider(snapshot.sourceProvider());
        view.setSourceObjectId(snapshot.sourceObjectId());
        view.setSourceCoordinateSystem(snapshot.sourceCoordinateSystem());
        view.setSourceGeometry(readJson(snapshot.sourceGeometryJson()));
        view.setDisplayCoordinateSystem(snapshot.displayCoordinateSystem());
        view.setDisplayGeometry(readJson(snapshot.displayGeometryJson()));
        view.setStatus(snapshot.status().name());
        view.setVersion(snapshot.version());
        view.setVerifiedBy(snapshot.verifiedBy());
        view.setVerifiedAt(snapshot.verifiedAt());
        view.setRemark(snapshot.remark());
        view.setCreatedAt(snapshot.createdAt());
        view.setUpdatedAt(snapshot.updatedAt());
        return view;
    }

    private SpatialFeatureCollectionSuccessResponse featureCollectionResponse(List<SpatialMapFeature> features) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SpatialGeoJsonFeatureCollection collection = new SpatialGeoJsonFeatureCollection();
        collection.setType("FeatureCollection");
        collection.setFeatures(features.stream().map(this::toFeature).toList());

        SpatialFeatureCollectionSuccessResponse response = new SpatialFeatureCollectionSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(collection);
        return response;
    }

    private SpatialGeoJsonFeature toFeature(SpatialMapFeature feature) {
        SpatialFeatureProperties properties = new SpatialFeatureProperties();
        properties.setEntityType(feature.entityType().name());
        properties.setEntityId(feature.entityId());
        properties.setEntityCode(feature.entityCode());
        properties.setName(feature.name());
        properties.setCommunityId(feature.communityId());
        properties.setStatus(feature.status().name());
        properties.setVersion(feature.version());
        properties.setCoordinateSystem(feature.coordinateSystem());
        properties.setSourceType(feature.sourceType());

        SpatialGeoJsonFeature result = new SpatialGeoJsonFeature();
        result.setType("Feature");
        result.setId(feature.entityId());
        result.setGeometry(readJson(feature.geometryJson()));
        result.setProperties(properties);
        return result;
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, GEO_JSON_OBJECT);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("空间几何 JSON 无法反序列化", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("空间几何 JSON 无法序列化", exception);
        }
    }
}
