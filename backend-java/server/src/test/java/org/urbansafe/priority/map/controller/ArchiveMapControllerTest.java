package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.CommunityBoundaryCandidateService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.model.api.ArchiveMapApi;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidatePreview;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidatePreviewSuccessResponse;
import org.urbansafe.priority.model.dto.CommunityBoundaryCandidateRequest;
import org.urbansafe.priority.model.dto.MapPlaceCandidateListSuccessResponse;
import org.urbansafe.priority.model.dto.PlaceSearchRequest;
import org.urbansafe.priority.model.dto.ReverseGeocodingRequest;
import org.urbansafe.priority.model.dto.ReverseGeocodingResultSuccessResponse;

/** 新增地图发现 HTTP 接口必须直接实现独立 archive OpenAPI 生成接口。 */
class ArchiveMapControllerTest {

    @Test
    void controllerImplementsGeneratedApiAndDelegatesPlaceSearch() {
        MapDiscoveryService discovery = mock(MapDiscoveryService.class);
        BusinessAccessService access = mock(BusinessAccessService.class);
        CommunityBoundaryCandidateService candidates = mock(CommunityBoundaryCandidateService.class);
        ArchiveMapController controller = new ArchiveMapController(discovery, access, candidates);
        PlaceSearchRequest request = mock(PlaceSearchRequest.class);
        when(request.getKeyword()).thenReturn("示范小区");
        when(request.getRegion()).thenReturn("株洲市");
        when(request.getCityLimit()).thenReturn(true);
        when(request.getPageSize()).thenReturn(8);
        when(discovery.searchPlaces("示范小区", "株洲市", true, 8)).thenReturn(List.of(Map.of(
                "name", "示范小区",
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MOCK",
                "coordinateSystem", "UNKNOWN",
                "mock", true)));

        ResponseEntity<MapPlaceCandidateListSuccessResponse> response = controller.searchArchivePlaces(request);

        assertThat(controller).isInstanceOf(ArchiveMapApi.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        verify(discovery).searchPlaces("示范小区", "株洲市", true, 8);
    }

    @Test
    void controllerDelegatesReverseGeocoding() {
        MapDiscoveryService discovery = mock(MapDiscoveryService.class);
        BusinessAccessService access = mock(BusinessAccessService.class);
        CommunityBoundaryCandidateService candidates = mock(CommunityBoundaryCandidateService.class);
        ArchiveMapController controller = new ArchiveMapController(discovery, access, candidates);
        ReverseGeocodingRequest request = mock(ReverseGeocodingRequest.class);
        when(request.getLongitude()).thenReturn(113.12);
        when(request.getLatitude()).thenReturn(27.88);
        when(discovery.reverseGeocode(113.12, 27.88)).thenReturn(Map.of(
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MOCK",
                "coordinateSystem", "UNKNOWN",
                "mock", true));

        ResponseEntity<ReverseGeocodingResultSuccessResponse> response = controller.previewArchiveReverseGeocoding(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getLongitude()).isEqualTo(113.12);
        verify(discovery).reverseGeocode(113.12, 27.88);
    }

    @Test
    void boundaryCandidatePreviewRequiresCommunityScopeAndDelegatesReadOnlyPreview() {
        MapDiscoveryService discovery = mock(MapDiscoveryService.class);
        BusinessAccessService access = mock(BusinessAccessService.class);
        CommunityBoundaryCandidateService candidates = mock(CommunityBoundaryCandidateService.class);
        ArchiveMapController controller = new ArchiveMapController(discovery, access, candidates);
        CommunityBoundaryCandidateRequest request = mock(CommunityBoundaryCandidateRequest.class);
        UUID communityId = UUID.randomUUID();
        when(request.getCommunityId()).thenReturn(communityId);
        when(request.getCommunityName()).thenReturn("示范小区");
        when(request.getAddress()).thenReturn("示范路1号");
        when(request.getRegion()).thenReturn("株洲市");
        when(candidates.preview("示范小区", "示范路1号", "株洲市")).thenReturn(Map.of(
                "available", false,
                "provider", "AMAP",
                "reasonCode", "DISABLED",
                "message", "未启用"));

        ResponseEntity<CommunityBoundaryCandidatePreviewSuccessResponse> response =
                controller.previewCommunityBoundaryCandidate(request);

        verify(access).assertCanReadCommunity(communityId);
        verify(candidates).preview("示范小区", "示范路1号", "株洲市");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getAvailable()).isFalse();
        assertThat(response.getBody().getData().getReasonCode())
                .isEqualTo(CommunityBoundaryCandidatePreview.ReasonCodeEnum.DISABLED);
    }

    @Test
    void generatedApiMethodsRequireDirectoryReadRole() throws Exception {
        assertThat(ArchiveMapApi.class.isAssignableFrom(ArchiveMapController.class)).isTrue();
        assertPreAuthorize(ArchiveMapController.class.getMethod("searchArchivePlaces", PlaceSearchRequest.class));
        assertPreAuthorize(ArchiveMapController.class.getMethod(
                "previewArchiveReverseGeocoding", ReverseGeocodingRequest.class));
        assertPreAuthorize(ArchiveMapController.class.getMethod(
                "previewCommunityBoundaryCandidate", CommunityBoundaryCandidateRequest.class));
    }

    private void assertPreAuthorize(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("%s 应声明目录读取权限", method.getName()).isNotNull();
        assertThat(annotation.value()).isEqualTo(BusinessAccessService.DIRECTORY_READ_ROLES);
    }
}
