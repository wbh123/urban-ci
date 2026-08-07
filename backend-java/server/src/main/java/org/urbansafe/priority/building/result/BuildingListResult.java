package org.urbansafe.priority.building.result;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 楼栋列表行的内部轻量结果。 */
public record BuildingListResult(UUID id, UUID communityId, String buildingCode, String buildingName,
        Integer constructionYear, Integer floorCount, Integer residentCount, String status,
        OffsetDateTime createdAt) { }
