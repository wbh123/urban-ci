package org.urbansafe.priority.spatial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.security.BusinessAccessService;

/** R2 边界生命周期：版本、确认状态和对象级授权必须由统一领域服务收口。 */
@ExtendWith(MockitoExtension.class)
class SpatialBoundaryServiceTest {

    @Mock
    private SpatialBoundaryRepository repository;

    @Mock
    private BusinessAccessService accessService;

    private SpatialBoundaryService service;

    @BeforeEach
    void setUp() {
        service = new SpatialBoundaryService(repository, accessService);
    }

    @Test
    void firstCommunityWriteCreatesVersionOneUnverifiedAndRevision() {
        UUID communityId = UUID.randomUUID();
        SpatialBoundaryWriteCommand command = command(0L, "首次录入");
        SpatialBoundarySnapshot created = snapshot(
                communityId, BoundaryEntityType.COMMUNITY, 1L, BoundaryStatus.UNVERIFIED, "首次录入");

        when(repository.findCurrent(BoundaryEntityType.COMMUNITY, communityId)).thenReturn(Optional.empty());
        when(repository.insertUnverified(BoundaryEntityType.COMMUNITY, communityId, command, 1L))
                .thenReturn(created);

        SpatialBoundarySnapshot result = service.upsertCommunity(communityId, command);

        assertThat(result.version()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(BoundaryStatus.UNVERIFIED);
        verify(accessService).assertCanManageCommunity(communityId);
        verify(repository).appendRevision(created, BoundaryChangeType.UPSERT, null);
    }

    @Test
    void matchingVersionUpdateCreatesNextUnverifiedRevision() {
        UUID buildingId = UUID.randomUUID();
        SpatialBoundarySnapshot current = snapshot(
                buildingId, BoundaryEntityType.BUILDING, 1L, BoundaryStatus.VERIFIED, "旧边界");
        SpatialBoundaryWriteCommand command = command(1L, "边界调整");
        SpatialBoundarySnapshot updated = snapshot(
                buildingId, BoundaryEntityType.BUILDING, 2L, BoundaryStatus.UNVERIFIED, "边界调整");

        when(repository.findCurrent(BoundaryEntityType.BUILDING, buildingId)).thenReturn(Optional.of(current));
        when(repository.updateUnverified(BoundaryEntityType.BUILDING, buildingId, command, 1L, 2L))
                .thenReturn(Optional.of(updated));

        SpatialBoundarySnapshot result = service.upsertBuilding(buildingId, command);

        assertThat(result.version()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo(BoundaryStatus.UNVERIFIED);
        verify(accessService).assertCanManageBuilding(buildingId);
        verify(repository).appendRevision(updated, BoundaryChangeType.UPSERT, null);
    }

    @Test
    void staleExpectedVersionReturnsStableConflictWithoutWriting() {
        UUID communityId = UUID.randomUUID();
        SpatialBoundarySnapshot current = snapshot(
                communityId, BoundaryEntityType.COMMUNITY, 3L, BoundaryStatus.UNVERIFIED, "当前版本");
        SpatialBoundaryWriteCommand stale = command(2L, "过期修改");
        when(repository.findCurrent(BoundaryEntityType.COMMUNITY, communityId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.upsertCommunity(communityId, stale))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).getErrorCode())
                        .isEqualTo("SPATIAL_BOUNDARY_VERSION_CONFLICT"));

        verify(repository, never()).updateUnverified(any(), any(), any(), any(Long.class), any(Long.class));
    }

    @Test
    void verifyCreatesNewVerifiedVersionAndRevision() {
        UUID communityId = UUID.randomUUID();
        SpatialBoundarySnapshot current = snapshot(
                communityId, BoundaryEntityType.COMMUNITY, 2L, BoundaryStatus.UNVERIFIED, "待确认");
        SpatialBoundarySnapshot verified = snapshot(
                communityId, BoundaryEntityType.COMMUNITY, 3L, BoundaryStatus.VERIFIED, "人工确认");

        when(repository.findCurrent(BoundaryEntityType.COMMUNITY, communityId)).thenReturn(Optional.of(current));
        when(repository.transitionStatus(
                        eq(BoundaryEntityType.COMMUNITY), eq(communityId), eq(2L), eq(3L),
                        eq(BoundaryStatus.VERIFIED), any(), eq("人工确认")))
                .thenReturn(Optional.of(verified));

        SpatialBoundarySnapshot result = service.verifyCommunity(communityId, 2L, "人工确认");

        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo(BoundaryStatus.VERIFIED);
        verify(accessService).assertCanManageCommunity(communityId);
        verify(repository).appendRevision(eq(verified), eq(BoundaryChangeType.VERIFY), any());
    }

    @Test
    void onlyUnverifiedBoundaryCanBeVerified() {
        UUID buildingId = UUID.randomUUID();
        SpatialBoundarySnapshot current = snapshot(
                buildingId, BoundaryEntityType.BUILDING, 5L, BoundaryStatus.VERIFIED, "已确认");
        when(repository.findCurrent(BoundaryEntityType.BUILDING, buildingId)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.verifyBuilding(buildingId, 5L, "重复确认"))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> assertThat(((ResourceConflictException) ex).getErrorCode())
                        .isEqualTo("SPATIAL_BOUNDARY_STATE_CONFLICT"));
    }

    private SpatialBoundaryWriteCommand command(long expectedVersion, String remark) {
        return new SpatialBoundaryWriteCommand(
                expectedVersion,
                "MANUAL_DRAW",
                "TEST",
                null,
                "GCJ02",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[113.0,27.0],[113.1,27.0],[113.1,27.1],[113.0,27.1],[113.0,27.0]]]]}",
                "GCJ02",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[113.0,27.0],[113.1,27.0],[113.1,27.1],[113.0,27.1],[113.0,27.0]]]]}",
                remark);
    }

    private SpatialBoundarySnapshot snapshot(
            UUID entityId,
            BoundaryEntityType entityType,
            long version,
            BoundaryStatus status,
            String remark) {
        OffsetDateTime now = OffsetDateTime.parse("2026-08-07T23:55:00+08:00");
        return new SpatialBoundarySnapshot(
                UUID.randomUUID(),
                entityType,
                entityId,
                "MANUAL_DRAW",
                "TEST",
                null,
                "GCJ02",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[]}",
                "GCJ02",
                "{\"type\":\"MultiPolygon\",\"coordinates\":[]}",
                status,
                version,
                null,
                status == BoundaryStatus.VERIFIED ? now : null,
                remark,
                now,
                now);
    }
}
