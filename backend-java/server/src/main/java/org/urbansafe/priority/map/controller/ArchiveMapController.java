package org.urbansafe.priority.map.controller;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.CommunityBoundaryCandidateService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.model.api.ArchiveMapApi;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidatePreview;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidatePreviewSuccessResponse;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidateRequest;
import org.urbansafe.priority.model.dto.MapPlaceCandidate;
import org.urbansafe.priority.model.dto.MapPlaceCandidateListSuccessResponse;
import org.urbansafe.priority.model.dto.PlaceSearchRequest;
import org.urbansafe.priority.model.dto.ReverseGeocodingRequest;
import org.urbansafe.priority.model.dto.ReverseGeocodingResult;
import org.urbansafe.priority.model.dto.ReverseGeocodingResultSuccessResponse;

/** 可视化建档地图发现接口。路由和请求模型由 archive OpenAPI 统一定义。 */
@RestController
public class ArchiveMapController implements ArchiveMapApi {

    private final MapDiscoveryService discovery;
    private final BusinessAccessService businessAccessService;
    private final CommunityBoundaryCandidateService boundaryCandidateService;

    public ArchiveMapController(
            MapDiscoveryService discovery,
            BusinessAccessService businessAccessService,
            CommunityBoundaryCandidateService boundaryCandidateService) {
        this.discovery = discovery;
        this.businessAccessService = businessAccessService;
        this.boundaryCandidateService = boundaryCandidateService;
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<MapPlaceCandidateListSuccessResponse> searchArchivePlaces(
            PlaceSearchRequest request) {
        boolean cityLimit = Boolean.TRUE.equals(request.getCityLimit());
        int pageSize = request.getPageSize() == null ? 8 : request.getPageSize();
        List<MapPlaceCandidate> candidates = discovery.searchPlaces(
                        request.getKeyword(), request.getRegion(), cityLimit, pageSize)
                .stream()
                .map(this::toPlaceCandidate)
                .toList();
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        MapPlaceCandidateListSuccessResponse response = new MapPlaceCandidateListSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(candidates);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<ReverseGeocodingResultSuccessResponse> previewArchiveReverseGeocoding(
            ReverseGeocodingRequest request) {
        Map<String, Object> result = discovery.reverseGeocode(
                request.getLongitude(), request.getLatitude());
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        ReverseGeocodingResultSuccessResponse response = new ReverseGeocodingResultSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(toReverseResult(result));
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<CommunityBoundaryCandidatePreviewSuccessResponse> previewCommunityBoundaryCandidate(
            CommunityBoundaryCandidateRequest request) {
        businessAccessService.assertCanReadCommunity(request.getCommunityId());
        Map<String, Object> result = boundaryCandidateService.preview(
                request.getCommunityName(), request.getAddress(), request.getRegion());

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CommunityBoundaryCandidatePreviewSuccessResponse response =
                new CommunityBoundaryCandidatePreviewSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(toBoundaryCandidatePreview(result));
        return ResponseEntity.ok(response);
    }

    private CommunityBoundaryCandidatePreview toBoundaryCandidatePreview(Map<String, Object> source) {
        CommunityBoundaryCandidatePreview preview = new CommunityBoundaryCandidatePreview();
        preview.setAvailable(Boolean.TRUE.equals(source.get("available")));
        preview.setProvider(CommunityBoundaryCandidatePreview.ProviderEnum.AMAP);
        String reasonCode = text(source.get("reasonCode"));
        if (reasonCode != null) {
            preview.setReasonCode(CommunityBoundaryCandidatePreview.ReasonCodeEnum.fromValue(reasonCode));
        }
        preview.setMessage(text(source.get("message")));
        String coordinateSystem = text(source.get("coordinateSystem"));
        if (coordinateSystem != null) {
            preview.setCoordinateSystem(
                    CommunityBoundaryCandidatePreview.CoordinateSystemEnum.fromValue(coordinateSystem));
        }
        String sourceType = text(source.get("sourceType"));
        if (sourceType != null) {
            preview.setSourceType(CommunityBoundaryCandidatePreview.SourceTypeEnum.fromValue(sourceType));
        }
        preview.setSourceId(text(source.get("sourceId")));
        preview.setName(text(source.get("name")));
        preview.setAddress(text(source.get("address")));
        Object geometry = source.get("geometry");
        if (geometry instanceof Map<?, ?> mapGeometry) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedGeometry = (Map<String, Object>) mapGeometry;
            preview.setGeometry(typedGeometry);
        }
        return preview;
    }

    private MapPlaceCandidate toPlaceCandidate(Map<String, Object> source) {
        MapPlaceCandidate candidate = new MapPlaceCandidate();
        candidate.setProviderObjectId(text(source.get("providerObjectId")));
        candidate.setName(text(source.get("name")));
        candidate.setFormattedAddress(text(source.get("formattedAddress")));
        candidate.setProvince(text(source.get("province")));
        candidate.setCity(text(source.get("city")));
        candidate.setDistrict(text(source.get("district")));
        candidate.setAdcode(text(source.get("adcode")));
        candidate.setCitycode(text(source.get("citycode")));
        candidate.setLongitude(number(source.get("longitude")));
        candidate.setLatitude(number(source.get("latitude")));
        candidate.setProvider(MapPlaceCandidate.ProviderEnum.fromValue(text(source.get("provider"))));
        candidate.setCoordinateSystem(MapPlaceCandidate.CoordinateSystemEnum.fromValue(
                text(source.get("coordinateSystem"))));
        candidate.setMock(Boolean.TRUE.equals(source.get("mock")));
        return candidate;
    }

    private ReverseGeocodingResult toReverseResult(Map<String, Object> source) {
        ReverseGeocodingResult result = new ReverseGeocodingResult();
        result.setFormattedAddress(text(source.get("formattedAddress")));
        result.setProvince(text(source.get("province")));
        result.setCity(text(source.get("city")));
        result.setDistrict(text(source.get("district")));
        result.setAdcode(text(source.get("adcode")));
        result.setCitycode(text(source.get("citycode")));
        result.setLongitude(number(source.get("longitude")));
        result.setLatitude(number(source.get("latitude")));
        result.setProvider(ReverseGeocodingResult.ProviderEnum.fromValue(text(source.get("provider"))));
        result.setCoordinateSystem(ReverseGeocodingResult.CoordinateSystemEnum.fromValue(
                text(source.get("coordinateSystem"))));
        result.setNearestPoiId(text(source.get("nearestPoiId")));
        result.setNearestPoiName(text(source.get("nearestPoiName")));
        result.setMock(Boolean.TRUE.equals(source.get("mock")));
        return result;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double number(Object value) {
        return value instanceof Number number
                ? number.doubleValue()
                : Double.valueOf(String.valueOf(value));
    }
}
