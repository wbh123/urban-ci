package org.urbansafe.priority.spatial;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 当前边界或修订快照的统一领域读取模型。 */
public record SpatialBoundarySnapshot(
        UUID id,
        BoundaryEntityType entityType,
        UUID entityId,
        String sourceType,
        String sourceProvider,
        String sourceObjectId,
        String sourceCoordinateSystem,
        String sourceGeometryJson,
        String displayCoordinateSystem,
        String displayGeometryJson,
        BoundaryStatus status,
        long version,
        UUID verifiedBy,
        OffsetDateTime verifiedAt,
        String remark,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
