package org.urbansafe.priority.building.command;

import java.math.BigDecimal;
import java.util.UUID;

/** 楼栋更新的内部业务命令，字段为空时表示保持原值。 */
public record UpdateBuildingCommand(Long version, UUID communityId, String buildingCode, String buildingName,
        String address, Integer constructionYear, String structureType, Integer floorCount,
        BigDecimal buildingArea, Integer householdCount, Integer residentCount, Integer elderlyCount,
        Integer childCount, Boolean hasElevator, Boolean hasIllegalModification,
        Boolean hasGroundFloorBusiness, String status, Object extraAttributes, String remark) {
    public Long getVersion() { return version; } public UUID getCommunityId() { return communityId; }
    public String getBuildingCode() { return buildingCode; } public String getBuildingName() { return buildingName; }
    public String getAddress() { return address; } public Integer getConstructionYear() { return constructionYear; }
    public String getStructureType() { return structureType; } public Integer getFloorCount() { return floorCount; }
    public BigDecimal getBuildingArea() { return buildingArea; } public Integer getHouseholdCount() { return householdCount; }
    public Integer getResidentCount() { return residentCount; } public Integer getElderlyCount() { return elderlyCount; }
    public Integer getChildCount() { return childCount; } public Boolean getHasElevator() { return hasElevator; }
    public Boolean getHasIllegalModification() { return hasIllegalModification; } public Boolean getHasGroundFloorBusiness() { return hasGroundFloorBusiness; }
    public String getStatus() { return status; } public Object getExtraAttributes() { return extraAttributes; }
    public String getRemark() { return remark; }
}
