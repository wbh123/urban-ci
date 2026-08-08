package org.urbansafe.priority.map.controller;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.ArchiveLocationService;
import org.urbansafe.priority.model.api.ArchiveLocationApi;
import org.urbansafe.priority.model.dto.BuildingLocation;
import org.urbansafe.priority.model.dto.BuildingLocationRequest;
import org.urbansafe.priority.model.dto.BuildingLocationSuccessResponse;

/** 楼栋地图中心点查询与保存接口。路由定义由 OpenAPI 生成接口统一提供。 */
@RestController
public class ArchiveLocationController implements ArchiveLocationApi {

    private final ArchiveLocationService service;

    public ArchiveLocationController(ArchiveLocationService service) {
        this.service = service;
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<BuildingLocationSuccessResponse> getArchiveBuildingLocation(UUID buildingId) {
        return ResponseEntity.ok(successResponse(service.getBuildingLocation(buildingId)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<BuildingLocationSuccessResponse> saveArchiveBuildingLocation(
            UUID buildingId,
            BuildingLocationRequest request) {
        return ResponseEntity.ok(successResponse(
                service.saveBuildingLocation(buildingId, toServiceRequest(request))));
    }

    private Map<String, Object> toServiceRequest(BuildingLocationRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("longitude", request.getLongitude());
        payload.put("latitude", request.getLatitude());
        putIfPresent(payload, "formattedAddress", request.getFormattedAddress());
        if (request.getProvider() != null) {
            payload.put("provider", request.getProvider().getValue());
        }
        if (request.getCoordinateSystem() != null) {
            payload.put("coordinateSystem", request.getCoordinateSystem().getValue());
        }
        putIfPresent(payload, "matchLevel", request.getMatchLevel());
        putIfPresent(payload, "mock", request.getMock());
        putIfPresent(payload, "metadata", request.getMetadata());
        return payload;
    }

    private BuildingLocationSuccessResponse successResponse(Map<String, Object> stored) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        BuildingLocationSuccessResponse response = new BuildingLocationSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(toLocation(stored));
        return response;
    }

    private BuildingLocation toLocation(Map<String, Object> stored) {
        BuildingLocation location = new BuildingLocation();
        location.setBuildingId(uuid(stored.get("buildingId")));
        location.setLongitude(number(stored.get("longitude")));
        location.setLatitude(number(stored.get("latitude")));
        location.setFormattedAddress(text(stored.get("formattedAddress")));
        location.setProvider(BuildingLocation.ProviderEnum.fromValue(text(stored.get("provider"))));
        location.setCoordinateSystem(BuildingLocation.CoordinateSystemEnum.fromValue(
                text(stored.get("coordinateSystem"))));
        location.setMatchLevel(text(stored.get("matchLevel")));
        location.setMetadata(metadata(stored.get("metadata")));
        if (stored.get("updatedAt") instanceof OffsetDateTime updatedAt) {
            location.setUpdatedAt(updatedAt);
        }
        return location;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private Double number(Object value) {
        return value instanceof Number number
                ? number.doubleValue()
                : Double.valueOf(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> metadata(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }
}
