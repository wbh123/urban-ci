package org.urbansafe.priority.spatial;

/**
 * 空间边界写入命令。
 *
 * @param expectedVersion 客户端读取到的当前版本；首次创建必须为 0
 * @param sourceType 来源类型
 * @param sourceProvider 来源提供方
 * @param sourceObjectId 外部来源对象标识，例如高德 AOI 标识
 * @param sourceCoordinateSystem 原始坐标系
 * @param sourceGeometryJson 原始 GeoJSON，原样保留以便追溯
 * @param displayCoordinateSystem 前端展示坐标系
 * @param displayGeometryJson 前端展示 MultiPolygon/Polygon GeoJSON
 * @param remark 说明
 */
public record SpatialBoundaryWriteCommand(
        long expectedVersion,
        String sourceType,
        String sourceProvider,
        String sourceObjectId,
        String sourceCoordinateSystem,
        String sourceGeometryJson,
        String displayCoordinateSystem,
        String displayGeometryJson,
        String remark) {
}
