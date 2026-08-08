package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;

/** R2 地图查询必须同时受 bbox、zoom 与当前用户小区范围约束。 */
@ExtendWith(MockitoExtension.class)
class SpatialGeoJsonServiceTest {

    @Mock
    private SpatialBoundaryRepository repository;

    @Mock
    private BusinessAccessService accessService;

    private SpatialGeoJsonService service;

    @BeforeEach
    void setUp() {
        service = new SpatialGeoJsonService(repository, accessService);
    }

    @Test
    void restrictedCommunityQueryPassesScopeAndLowZoomToleranceToRepository() {
        UUID allowedCommunityId = UUID.randomUUID();
        CommunityAccessScope scope = CommunityAccessScope.restricted(Set.of(allowedCommunityId));
        when(accessService.currentCommunityScope()).thenReturn(scope);

        service.queryCommunities(113.0, 27.0, 114.0, 28.0, 11);

        verify(repository).queryVerifiedCommunities(
                eq(113.0), eq(27.0), eq(114.0), eq(28.0), eq(0.00008), eq(scope));
    }

    @Test
    void highZoomKeepsOriginalGeometryWithoutSimplification() {
        CommunityAccessScope scope = CommunityAccessScope.globalScope();
        when(accessService.currentCommunityScope()).thenReturn(scope);

        service.queryCommunities(113.0, 27.0, 114.0, 28.0, 18);

        verify(repository).queryVerifiedCommunities(
                eq(113.0), eq(27.0), eq(114.0), eq(28.0), eq(0.0), eq(scope));
    }

    @Test
    void buildingQueryWithCommunityFilterPerformsObjectScopeCheck() {
        UUID communityId = UUID.randomUUID();
        CommunityAccessScope scope = CommunityAccessScope.globalScope();
        when(accessService.currentCommunityScope()).thenReturn(scope);

        service.queryBuildings(113.0, 27.0, 114.0, 28.0, 15, communityId);

        verify(accessService).assertCanReadCommunity(communityId);
        verify(repository).queryVerifiedBuildings(
                eq(113.0), eq(27.0), eq(114.0), eq(28.0), eq(0.000005), eq(communityId), eq(scope));
    }

    @Test
    void invalidBboxIsRejectedBeforeRepositoryQuery() {
        assertThatThrownBy(() -> service.queryCommunities(114.0, 27.0, 113.0, 28.0, 15))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                ((InvalidRequestException) ex).getErrorCode())
                        .isEqualTo("SPATIAL_BBOX_INVALID"));
    }

    @Test
    void unsupportedZoomIsRejected() {
        assertThatThrownBy(() -> service.queryCommunities(113.0, 27.0, 114.0, 28.0, 23))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                                ((InvalidRequestException) ex).getErrorCode())
                        .isEqualTo("SPATIAL_ZOOM_INVALID"));
    }
}
