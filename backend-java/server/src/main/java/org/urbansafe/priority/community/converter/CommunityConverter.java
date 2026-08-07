package org.urbansafe.priority.community.converter;

import org.springframework.stereotype.Component;
import org.urbansafe.priority.community.command.CreateCommunityCommand;
import org.urbansafe.priority.community.command.UpdateCommunityCommand;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.community.result.CommunityListResult;
import org.urbansafe.priority.model.dto.CommunityListRow;
import org.urbansafe.priority.model.dto.CommunityResponse;
import org.urbansafe.priority.model.dto.CreateCommunityRequest;
import org.urbansafe.priority.model.dto.UpdateCommunityRequest;
import org.urbansafe.priority.persistence.entity.CommunityEntity;

/**
 * 小区三层模型转换器：OpenAPI DTO、业务 Command/Result 与持久化 Entity 的转换集中在此处。
 */
@Component
public class CommunityConverter {

    /** 将 OpenAPI 创建请求转换为业务命令。 */
    public CreateCommunityCommand toCommand(CreateCommunityRequest request) {
        return new CreateCommunityCommand(
                request.getCommunityCode(),
                request.getCommunityName(),
                request.getAdministrativeRegion(),
                request.getAddress(),
                request.getConstructionPeriod(),
                request.getHouseholdCount(),
                request.getResidentCount(),
                request.getStatus() == null ? null : request.getStatus().getValue(),
                request.getExtraAttributes(),
                request.getRemark());
    }

    /** 将 OpenAPI 更新请求转换为业务命令。 */
    public UpdateCommunityCommand toCommand(UpdateCommunityRequest request) {
        return new UpdateCommunityCommand(
                request.getVersion(),
                request.getCommunityCode(),
                request.getCommunityName(),
                request.getAdministrativeRegion(),
                request.getAddress(),
                request.getConstructionPeriod(),
                request.getHouseholdCount(),
                request.getResidentCount(),
                request.getStatus() == null ? null : request.getStatus().getValue(),
                request.getExtraAttributes(),
                request.getRemark());
    }

    /** 将持久化实体转换为不含数据库注解的业务详情结果。 */
    public CommunityDetailResult toDetailResult(CommunityEntity entity) {
        return new CommunityDetailResult(
                entity.getId(), entity.getCommunityCode(), entity.getCommunityName(),
                entity.getAdministrativeRegion(), entity.getAddress(), entity.getConstructionPeriod(),
                entity.getBuildingCount(), entity.getHouseholdCount(), entity.getResidentCount(),
                entity.getArchiveCompletenessScore(), entity.getStatus(), entity.getExtraAttributes(),
                entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion());
    }

    /** 将持久化实体转换为分页列表业务结果。 */
    public CommunityListResult toListResult(CommunityEntity entity) {
        return new CommunityListResult(
                entity.getId(), entity.getCommunityCode(), entity.getCommunityName(),
                entity.getAdministrativeRegion(), entity.getAddress(), entity.getBuildingCount(),
                entity.getHouseholdCount(), entity.getResidentCount(), entity.getStatus(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    /** 将业务详情结果转换为 OpenAPI 响应 DTO。 */
    public CommunityResponse toResponse(CommunityDetailResult result) {
        CommunityResponse response = new CommunityResponse();
        response.setId(result.id());
        response.setCommunityCode(result.communityCode());
        response.setCommunityName(result.communityName());
        response.setAdministrativeRegion(result.administrativeRegion());
        response.setAddress(result.address());
        response.setConstructionPeriod(result.constructionPeriod());
        response.setBuildingCount(result.buildingCount());
        response.setHouseholdCount(result.householdCount());
        response.setResidentCount(result.residentCount());
        if (result.archiveCompletenessScore() != null) {
            response.setArchiveCompletenessScore(result.archiveCompletenessScore().floatValue());
        }
        if (result.status() != null) {
            response.setStatus(CommunityResponse.StatusEnum.fromValue(result.status()));
        }
        response.setExtraAttributes(result.extraAttributes());
        response.setRemark(result.remark());
        response.setCreatedAt(result.createdAt());
        response.setUpdatedAt(result.updatedAt());
        response.setVersion(result.version());
        return response;
    }

    /** 将分页业务结果转换为 OpenAPI 列表行 DTO。 */
    public CommunityListRow toListRow(CommunityListResult result) {
        CommunityListRow row = new CommunityListRow();
        row.setId(result.id());
        row.setCommunityCode(result.communityCode());
        row.setCommunityName(result.communityName());
        row.setAdministrativeRegion(result.administrativeRegion());
        row.setAddress(result.address());
        row.setBuildingCount(result.buildingCount());
        row.setHouseholdCount(result.householdCount());
        row.setResidentCount(result.residentCount());
        if (result.status() != null) {
            row.setStatus(CommunityListRow.StatusEnum.fromValue(result.status()));
        }
        row.setCreatedAt(result.createdAt());
        row.setUpdatedAt(result.updatedAt());
        return row;
    }
}
