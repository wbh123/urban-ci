package org.urbansafe.priority.building.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.command.UpdateBuildingCommand;
import org.urbansafe.priority.building.converter.BuildingConverter;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageAdapter;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.mapperext.CommunityMapperExt;
import org.urbansafe.priority.persistence.entity.BuildingEvidenceEntity;
import org.urbansafe.priority.persistence.mapper.BuildingEvidenceMapper;

@Service
public class BuildingServiceImpl implements BuildingService {

    private static final Logger LOGGER = LogManager.getLogger(BuildingServiceImpl.class);

    private final BuildingMapper buildingMapper;
    private final CommunityMapper communityMapper;
    private final CommunityMapperExt communityMapperExt;
    private final BuildingEvidenceMapper buildingEvidenceMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final BuildingConverter buildingConverter;

    public BuildingServiceImpl(
            BuildingMapper buildingMapper,
            CommunityMapper communityMapper,
            CommunityMapperExt communityMapperExt,
            BuildingEvidenceMapper buildingEvidenceMapper,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock, BuildingConverter buildingConverter) {
        this.buildingMapper = buildingMapper;
        this.communityMapper = communityMapper;
        this.communityMapperExt = communityMapperExt;
        this.buildingEvidenceMapper = buildingEvidenceMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.buildingConverter = buildingConverter;
    }

    @Override
    @Transactional
    public BuildingDetailResult createBuilding(CreateBuildingCommand request) {
        CommunityEntity community = communityMapper.selectById(request.getCommunityId());
        if (community == null) {
            throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "所属小区不存在");
        }

        long codeConflict = buildingMapper.selectCount(
                new LambdaQueryWrapper<BuildingEntity>()
                        .eq(BuildingEntity::getCommunityId, request.getCommunityId())
                        .eq(BuildingEntity::getBuildingCode, normalizeCode(request.getBuildingCode())));
        if (codeConflict > 0) {
            throw new ResourceConflictException("BUILDING_CODE_CONFLICT", "同小区内楼栋编码已存在");
        }

        validatePopulationRelation(request.getResidentCount(), request.getElderlyCount(), request.getChildCount());

        BuildingEntity entity = new BuildingEntity();
        entity.setCommunityId(request.getCommunityId());
        entity.setBuildingCode(normalizeCode(request.getBuildingCode()));
        entity.setBuildingName(request.getBuildingName());
        entity.setAddress(request.getAddress());
        entity.setConstructionYear(request.getConstructionYear() != null
                ? request.getConstructionYear().shortValue() : null);
        entity.setStructureType(request.getStructureType());
        entity.setFloorCount(request.getFloorCount());
        entity.setBuildingArea(request.getBuildingArea());
        entity.setHouseholdCount(request.getHouseholdCount());
        entity.setResidentCount(request.getResidentCount());
        entity.setElderlyCount(request.getElderlyCount());
        entity.setChildCount(request.getChildCount());
        entity.setHasElevator(request.getHasElevator());
        entity.setHasIllegalModification(request.getHasIllegalModification());
        entity.setHasGroundFloorBusiness(request.getHasGroundFloorBusiness());
        entity.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        entity.setExtraAttributes(toJsonNode(request.getExtraAttributes()));
        entity.setRemark(request.getRemark());
        entity.setVersion(0L);

        buildingMapper.insert(entity);
        communityMapperExt.refreshBuildingCount(request.getCommunityId());

        auditService.recordSuccess(AuditOperation.success(
                "CREATE", "Building", entity.getId(), null, entity,
                List.of(), "创建楼栋"));

        return buildingConverter.toDetailResult(entity);
    }

    @Override
    public PageResult<BuildingListResult> listBuildings(UUID communityId, String keyword, ApiPageRequest pageRequest, String sort) {
        LambdaQueryWrapper<BuildingEntity> wrapper = new LambdaQueryWrapper<>();

        if (communityId != null) {
            wrapper.eq(BuildingEntity::getCommunityId, communityId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(BuildingEntity::getBuildingCode, keyword)
                    .or().like(BuildingEntity::getBuildingName, keyword)
                    .or().like(BuildingEntity::getAddress, keyword));
        }

        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", -1);
            if (parts.length > 2 || parts[0].isBlank()
                    || (parts.length == 2
                            && !"asc".equalsIgnoreCase(parts[1])
                            && !"desc".equalsIgnoreCase(parts[1]))) {
                throw new InvalidRequestException("INVALID_SORT_FIELD", "排序格式必须为 field,asc 或 field,desc");
            }
            String field = parts[0];
            boolean asc = parts.length == 1 || "asc".equalsIgnoreCase(parts[1]);
            switch (field) {
                case "buildingCode" -> wrapper.orderBy(true, asc, BuildingEntity::getBuildingCode);
                case "buildingName" -> wrapper.orderBy(true, asc, BuildingEntity::getBuildingName);
                case "constructionYear" -> wrapper.orderBy(true, asc, BuildingEntity::getConstructionYear);
                case "floorCount" -> wrapper.orderBy(true, asc, BuildingEntity::getFloorCount);
                case "residentCount" -> wrapper.orderBy(true, asc, BuildingEntity::getResidentCount);
                case "createdAt" -> wrapper.orderBy(true, asc, BuildingEntity::getCreatedAt);
                default -> throw new InvalidRequestException("INVALID_SORT_FIELD", "不支持的楼栋排序字段: " + field);
            }
        } else {
            wrapper.orderByDesc(BuildingEntity::getCreatedAt);
        }

        Page<BuildingEntity> pageResult = buildingMapper.selectPage(
                PageAdapter.toMyBatisPage(pageRequest), wrapper);
        return PageAdapter.toApiPage(pageResult, buildingConverter::toListResult);
    }

    @Override
    public BuildingDetailResult getBuilding(UUID buildingId) {
        BuildingEntity entity = buildingMapper.selectById(buildingId);
        if (entity == null) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }
        return buildingConverter.toDetailResult(entity);
    }

    @Override
    @Transactional
    public BuildingDetailResult updateBuilding(UUID buildingId, UpdateBuildingCommand request) {
        BuildingEntity entity = buildingMapper.selectById(buildingId);
        if (entity == null) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }

        if (!request.getVersion().equals(entity.getVersion())) {
            throw new ResourceConflictException("RESOURCE_VERSION_CONFLICT", "楼栋已被其他请求修改，请刷新后重试");
        }

        JsonNode beforeData = objectMapper.valueToTree(entity);

        UUID oldCommunityId = entity.getCommunityId();
        UUID targetCommunityId = request.getCommunityId() == null
                ? oldCommunityId
                : request.getCommunityId();
        String targetBuildingCode = request.getBuildingCode() == null
                ? entity.getBuildingCode()
                : normalizeCode(request.getBuildingCode());

        if (!targetCommunityId.equals(oldCommunityId)) {
            CommunityEntity targetCommunity = communityMapper.selectById(targetCommunityId);
            if (targetCommunity == null) {
                throw new ResourceNotFoundException("COMMUNITY_NOT_FOUND", "目标小区不存在");
            }
        }

        long codeConflict = buildingMapper.selectCount(
                new LambdaQueryWrapper<BuildingEntity>()
                        .eq(BuildingEntity::getCommunityId, targetCommunityId)
                        .eq(BuildingEntity::getBuildingCode, targetBuildingCode)
                        .ne(BuildingEntity::getId, buildingId));
        if (codeConflict > 0) {
            throw new ResourceConflictException("BUILDING_CODE_CONFLICT", "目标小区内楼栋编码已存在");
        }

        boolean communityChanged = !targetCommunityId.equals(oldCommunityId);
        entity.setCommunityId(targetCommunityId);
        entity.setBuildingCode(targetBuildingCode);


        if (request.getBuildingName() != null) entity.setBuildingName(request.getBuildingName());
        if (request.getAddress() != null) entity.setAddress(request.getAddress());
        if (request.getConstructionYear() != null)
            entity.setConstructionYear(request.getConstructionYear().shortValue());
        if (request.getStructureType() != null) entity.setStructureType(request.getStructureType());
        if (request.getFloorCount() != null) entity.setFloorCount(request.getFloorCount());
        if (request.getBuildingArea() != null) entity.setBuildingArea(request.getBuildingArea());
        if (request.getHouseholdCount() != null) entity.setHouseholdCount(request.getHouseholdCount());
        if (request.getResidentCount() != null) entity.setResidentCount(request.getResidentCount());
        if (request.getElderlyCount() != null) entity.setElderlyCount(request.getElderlyCount());
        if (request.getChildCount() != null) entity.setChildCount(request.getChildCount());
        if (request.getHasElevator() != null) entity.setHasElevator(request.getHasElevator());
        if (request.getHasIllegalModification() != null)
            entity.setHasIllegalModification(request.getHasIllegalModification());
        if (request.getHasGroundFloorBusiness() != null)
            entity.setHasGroundFloorBusiness(request.getHasGroundFloorBusiness());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getExtraAttributes() != null)
            entity.setExtraAttributes(toJsonNode(request.getExtraAttributes()));
        if (request.getRemark() != null) entity.setRemark(request.getRemark());

        validatePopulationRelation(entity.getResidentCount(), entity.getElderlyCount(), entity.getChildCount());

        int updatedRows = buildingMapper.updateById(entity);
        if (updatedRows == 0) {
            throw new ResourceConflictException("RESOURCE_VERSION_CONFLICT", "楼栋已被其他请求修改，请刷新后重试");
        }

        communityMapperExt.refreshBuildingCount(oldCommunityId);
        if (communityChanged) {
            communityMapperExt.refreshBuildingCount(targetCommunityId);
        }

        auditService.recordSuccess(AuditOperation.success(
                "UPDATE", "Building", buildingId, beforeData, entity,
                changedBuildingFields(request), "修改楼栋"));

        return buildingConverter.toDetailResult(entity);
    }

    @Override
    @Transactional
    public void deleteBuilding(UUID buildingId) {
        BuildingEntity entity = buildingMapper.selectById(buildingId);
        if (entity == null) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "楼栋不存在");
        }

        long evidenceCount = buildingEvidenceMapper.selectCount(
                new LambdaQueryWrapper<BuildingEvidenceEntity>()
                        .eq(BuildingEvidenceEntity::getBuildingId, buildingId));

        buildingMapper.deleteById(buildingId);
        communityMapperExt.refreshBuildingCount(entity.getCommunityId());

        auditService.recordSuccess(AuditOperation.success(
                "DELETE", "Building", buildingId, entity, null,
                List.of("deletedAt"), "删除楼栋，仍保留证据数量: " + evidenceCount));

    }

    private void validatePopulationRelation(Integer residentCount, Integer elderlyCount, Integer childCount) {
        if (residentCount != null && elderlyCount != null && elderlyCount > residentCount) {
            throw new InvalidRequestException(
                    "BUILDING_POPULATION_RELATION_INVALID", "老年人数不能超过居民总数");
        }
        if (residentCount != null && childCount != null && childCount > residentCount) {
            throw new InvalidRequestException(
                    "BUILDING_POPULATION_RELATION_INVALID", "儿童人数不能超过居民总数");
        }
        if (elderlyCount != null && childCount != null && residentCount != null
                && elderlyCount + childCount > residentCount) {
            throw new InvalidRequestException("BUILDING_POPULATION_RELATION_INVALID",
                    "老年人数与儿童人数之和不能超过居民总数");
        }
    }

    private JsonNode toJsonNode(Object obj) {
        return obj == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(obj);
    }

    /**
     * 对楼栋编码执行统一的去空格和大写规范化。
     *
     * @param code 客户端编码
     * @return 规范化编码
     */
    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 计算更新请求涉及的字段名，供审计日志展示变更范围。
     *
     * @param request 楼栋更新请求
     * @return 有值字段名列表
     */
    private List<String> changedBuildingFields(UpdateBuildingCommand request) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (request.getCommunityId() != null) fields.add("communityId");
        if (request.getBuildingCode() != null) fields.add("buildingCode");
        if (request.getBuildingName() != null) fields.add("buildingName");
        if (request.getAddress() != null) fields.add("address");
        if (request.getConstructionYear() != null) fields.add("constructionYear");
        if (request.getStructureType() != null) fields.add("structureType");
        if (request.getFloorCount() != null) fields.add("floorCount");
        if (request.getBuildingArea() != null) fields.add("buildingArea");
        if (request.getHouseholdCount() != null) fields.add("householdCount");
        if (request.getResidentCount() != null) fields.add("residentCount");
        if (request.getElderlyCount() != null) fields.add("elderlyCount");
        if (request.getChildCount() != null) fields.add("childCount");
        if (request.getHasElevator() != null) fields.add("hasElevator");
        if (request.getHasIllegalModification() != null) fields.add("hasIllegalModification");
        if (request.getHasGroundFloorBusiness() != null) fields.add("hasGroundFloorBusiness");
        if (request.getStatus() != null) fields.add("status");
        if (request.getExtraAttributes() != null) fields.add("extraAttributes");
        if (request.getRemark() != null) fields.add("remark");
        return List.copyOf(fields);
    }
}
