package org.urbansafe.priority.spatial;

/** 空间边界所属业务对象。表名和外键列只由该枚举提供，禁止使用客户端字符串拼接 SQL。 */
public enum BoundaryEntityType {
    COMMUNITY("geo.community_boundary", "community_id"),
    BUILDING("geo.building_boundary", "building_id");

    private final String tableName;
    private final String entityColumn;

    BoundaryEntityType(String tableName, String entityColumn) {
        this.tableName = tableName;
        this.entityColumn = entityColumn;
    }

    String tableName() {
        return tableName;
    }

    String entityColumn() {
        return entityColumn;
    }
}
