package org.urbansafe.priority.spatial;

import java.util.UUID;

/** bbox 查询返回给 API 层的已确认空间要素投影。 */
public record SpatialMapFeature(
        UUID entityId,
        BoundaryEntityType entityType,
        String entityCode,
        String name,
        UUID communityId,
        String coordinateSystem,
        BoundaryStatus status,
        long version,
        String sourceType,
        String geometryJson) {
}
