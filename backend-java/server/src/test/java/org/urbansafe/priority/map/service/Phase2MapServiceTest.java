package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;
import org.urbansafe.priority.phase2.repository.Phase2Repository;

@ExtendWith(MockitoExtension.class)
class Phase2MapServiceTest {

    @Mock
    private Phase2Repository repository;

    private Phase2MapService service;

    @BeforeEach
    void setUp() {
        service = new Phase2MapService(new MapProperties(), new AmapProperties(), repository);
    }

    @Test
    void savesMockGeocodingResultWithExplicitUnknownCoordinateSystem() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        when(repository.json(any())).thenReturn("{\"mock\":true}");
        when(repository.saveCommunityLocation(
                eq(communityId), eq(113.13), eq(27.82), eq("示范小区"),
                eq("MOCK"), eq("UNKNOWN"), eq("MOCK_PREVIEW"), eq("{\"mock\":true}")))
                .thenReturn(Map.of("provider", "MOCK", "coordinateSystem", "UNKNOWN"));

        Map<String, Object> result = service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "formattedAddress", "示范小区",
                "provider", "MOCK",
                "coordinateSystem", "UNKNOWN",
                "matchLevel", "MOCK_PREVIEW",
                "mock", true));

        assertThat(result)
                .containsEntry("provider", "MOCK")
                .containsEntry("coordinateSystem", "UNKNOWN");
    }

    @Test
    void manualLocationDefaultsToUnknownCoordinateSystemInsteadOfPretendingGcj02() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        when(repository.json(any())).thenReturn("{}");
        when(repository.saveCommunityLocation(
                eq(communityId), eq(113.13), eq(27.82), eq("人工录入"),
                eq("MANUAL"), eq("UNKNOWN"), eq("MANUAL_POINT"), eq("{}")))
                .thenReturn(Map.of("provider", "MANUAL", "coordinateSystem", "UNKNOWN"));

        Map<String, Object> result = service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "formattedAddress", "人工录入",
                "provider", "MANUAL",
                "matchLevel", "MANUAL_POINT"));

        assertThat(result).containsEntry("coordinateSystem", "UNKNOWN");
    }

    @Test
    void amapLocationDefaultsToGcj02WhenLegacyCallerOmitsCoordinateSystem() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        when(repository.json(any())).thenReturn("{}");
        when(repository.saveCommunityLocation(
                eq(communityId), eq(113.13), eq(27.82), eq("高德候选"),
                eq("AMAP"), eq("GCJ02"), eq("PLACE_SEARCH"), eq("{}")))
                .thenReturn(Map.of("provider", "AMAP", "coordinateSystem", "GCJ02"));

        Map<String, Object> result = service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "formattedAddress", "高德候选",
                "provider", "AMAP",
                "matchLevel", "PLACE_SEARCH"));

        assertThat(result).containsEntry("coordinateSystem", "GCJ02");
    }

    @Test
    void preservesExplicitCoordinateSystemForImportedLocation() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        when(repository.json(any())).thenReturn("{}");
        when(repository.saveCommunityLocation(
                eq(communityId), eq(113.13), eq(27.82), eq("导入点位"),
                eq("IMPORT"), eq("WGS84"), eq("IMPORT"), eq("{}")))
                .thenReturn(Map.of("provider", "IMPORT", "coordinateSystem", "WGS84"));

        Map<String, Object> result = service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "formattedAddress", "导入点位",
                "provider", "IMPORT",
                "coordinateSystem", "WGS84",
                "matchLevel", "IMPORT"));

        assertThat(result).containsEntry("coordinateSystem", "WGS84");
    }

    @Test
    void rejectsUnsupportedProviderBeforeWritingDatabase() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);

        assertThatThrownBy(() -> service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "provider", "UNKNOWN_PROVIDER")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("provider");

        verify(repository, never()).saveCommunityLocation(
                any(), anyDouble(), anyDouble(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnsupportedCoordinateSystemBeforeWritingDatabase() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);

        assertThatThrownBy(() -> service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "provider", "MANUAL",
                "coordinateSystem", "EPSG3857")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("coordinateSystem");

        verify(repository, never()).saveCommunityLocation(
                any(), anyDouble(), anyDouble(), any(), any(), any(), any(), any());
    }
}
