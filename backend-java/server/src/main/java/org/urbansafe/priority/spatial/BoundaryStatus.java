package org.urbansafe.priority.spatial;

/** 边界审核状态。只有 VERIFIED 边界允许进入正式地图查询。 */
public enum BoundaryStatus {
    UNVERIFIED,
    VERIFIED,
    REJECTED
}
