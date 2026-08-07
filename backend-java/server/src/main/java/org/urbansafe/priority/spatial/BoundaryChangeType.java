package org.urbansafe.priority.spatial;

/** 空间边界不可变修订记录的变更类型。 */
public enum BoundaryChangeType {
    UPSERT,
    VERIFY,
    REJECT
}
