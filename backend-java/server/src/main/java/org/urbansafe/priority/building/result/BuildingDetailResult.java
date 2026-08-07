package org.urbansafe.priority.building.result;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 楼栋详情的内部业务结果，禁止把持久化实体或 OpenAPI DTO 传出 Service。 */
public record BuildingDetailResult(UUID id, UUID communityId, String buildingCode, String buildingName,
        String address, Integer constructionYear, String structureType, Integer floorCount,
        BigDecimal buildingArea, Integer householdCount, Integer residentCount, Integer elderlyCount,
        Integer childCount, Boolean hasElevator, Boolean hasIllegalModification,
        Boolean hasGroundFloorBusiness, BigDecimal archiveCompletenessScore, String status,
        JsonNode extraAttributes, String remark, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        Long version) { }
