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
    void savesMockGeocodingResultWithExplicitMockProvider() {
        UUID communityId = UUID.randomUUID();
        when(repository.communityExists(communityId)).thenReturn(true);
        when(repository.json(any())).thenReturn("{\"mock\":true}");
        when(repository.saveCommunityLocation(
                eq(communityId), eq(113.13), eq(27.82), eq("示范小区"),
                eq("MOCK"), eq("MOCK_PREVIEW"), eq("{\"mock\":true}")))
                .thenReturn(Map.of("provider", "MOCK"));

        Map<String, Object> result = service.saveLocation(communityId, Map.of(
                "longitude", 113.13,
                "latitude", 27.82,
                "formattedAddress", "示范小区",
                "provider", "MOCK",
                "matchLevel", "MOCK_PREVIEW",
                "mock", true));

        assertThat(result).containsEntry("provider", "MOCK");
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
                any(), anyDouble(), anyDouble(), any(), any(), any(), any());
    }
}
