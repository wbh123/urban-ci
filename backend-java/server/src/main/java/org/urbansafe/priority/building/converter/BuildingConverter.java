package org.urbansafe.priority.building.converter;

import org.springframework.stereotype.Component;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.command.UpdateBuildingCommand;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.model.dto.BuildingListRow;
import org.urbansafe.priority.model.dto.BuildingResponse;
import org.urbansafe.priority.model.dto.CreateBuildingRequest;
import org.urbansafe.priority.model.dto.UpdateBuildingRequest;
import org.urbansafe.priority.persistence.entity.BuildingEntity;

/** 集中执行楼栋 OpenAPI DTO、内部对象和持久化实体的转换。 */
@Component
public class BuildingConverter {
    /** 将 OpenAPI 创建请求转换为内部命令。 */
    public CreateBuildingCommand toCommand(CreateBuildingRequest request) {
        return new CreateBuildingCommand(request.getCommunityId(), request.getBuildingCode(), request.getBuildingName(), request.getAddress(), request.getConstructionYear(), request.getStructureType(), request.getFloorCount(), request.getBuildingArea(), request.getHouseholdCount(), request.getResidentCount(), request.getElderlyCount(), request.getChildCount(), request.getHasElevator(), request.getHasIllegalModification(), request.getHasGroundFloorBusiness(), request.getStatus() == null ? null : request.getStatus().getValue(), request.getExtraAttributes(), request.getRemark());
    }
    /** 将 OpenAPI 更新请求转换为内部命令。 */
    public UpdateBuildingCommand toCommand(UpdateBuildingRequest request) {
        return new UpdateBuildingCommand(request.getVersion(), request.getCommunityId(), request.getBuildingCode(), request.getBuildingName(), request.getAddress(), request.getConstructionYear(), request.getStructureType(), request.getFloorCount(), request.getBuildingArea(), request.getHouseholdCount(), request.getResidentCount(), request.getElderlyCount(), request.getChildCount(), request.getHasElevator(), request.getHasIllegalModification(), request.getHasGroundFloorBusiness(), request.getStatus() == null ? null : request.getStatus().getValue(), request.getExtraAttributes(), request.getRemark());
    }
    /** 将实体转换为内部详情结果。 */
    public BuildingDetailResult toDetailResult(BuildingEntity entity) {
        return new BuildingDetailResult(entity.getId(), entity.getCommunityId(), entity.getBuildingCode(), entity.getBuildingName(), entity.getAddress(), entity.getConstructionYear() == null ? null : entity.getConstructionYear().intValue(), entity.getStructureType(), entity.getFloorCount(), entity.getBuildingArea(), entity.getHouseholdCount(), entity.getResidentCount(), entity.getElderlyCount(), entity.getChildCount(), entity.getHasElevator(), entity.getHasIllegalModification(), entity.getHasGroundFloorBusiness(), entity.getArchiveCompletenessScore(), entity.getStatus(), entity.getExtraAttributes(), entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }
    /** 将实体转换为内部列表结果。 */
    public BuildingListResult toListResult(BuildingEntity entity) {
        return new BuildingListResult(entity.getId(), entity.getCommunityId(), entity.getBuildingCode(), entity.getBuildingName(), entity.getConstructionYear() == null ? null : entity.getConstructionYear().intValue(), entity.getFloorCount(), entity.getResidentCount(), entity.getStatus(), entity.getCreatedAt());
    }
    /** 将内部详情结果转换为生成的 OpenAPI 响应。 */
    public BuildingResponse toResponse(BuildingDetailResult result) {
        BuildingResponse response = new BuildingResponse(); response.setId(result.id()); response.setCommunityId(result.communityId()); response.setBuildingCode(result.buildingCode()); response.setBuildingName(result.buildingName()); response.setAddress(result.address()); response.setConstructionYear(result.constructionYear()); response.setStructureType(result.structureType()); response.setFloorCount(result.floorCount()); response.setBuildingArea(result.buildingArea()); response.setHouseholdCount(result.householdCount()); response.setResidentCount(result.residentCount()); response.setElderlyCount(result.elderlyCount()); response.setChildCount(result.childCount()); response.setHasElevator(result.hasElevator()); response.setHasIllegalModification(result.hasIllegalModification()); response.setHasGroundFloorBusiness(result.hasGroundFloorBusiness()); response.setArchiveCompletenessScore(result.archiveCompletenessScore()); if (result.status() != null) response.setStatus(BuildingResponse.StatusEnum.fromValue(result.status())); response.setExtraAttributes(result.extraAttributes()); response.setRemark(result.remark()); response.setCreatedAt(result.createdAt()); response.setUpdatedAt(result.updatedAt()); response.setVersion(result.version()); return response;
    }
    /** 将内部列表结果转换为生成的 OpenAPI 列表行。 */
    public BuildingListRow toListRow(BuildingListResult result) {
        BuildingListRow row = new BuildingListRow(); row.setId(result.id()); row.setCommunityId(result.communityId()); row.setBuildingCode(result.buildingCode()); row.setBuildingName(result.buildingName()); row.setConstructionYear(result.constructionYear()); row.setFloorCount(result.floorCount()); row.setResidentCount(result.residentCount()); row.setStatus(result.status()); row.setCreatedAt(result.createdAt()); return row;
    }
}
