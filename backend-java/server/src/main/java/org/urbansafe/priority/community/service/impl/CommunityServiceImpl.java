package org.urbansafe.priority.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.community.service.CommunityService;
import org.urbansafe.priority.community.command.CreateCommunityCommand;
import org.urbansafe.priority.community.command.UpdateCommunityCommand;
import org.urbansafe.priority.community.converter.CommunityConverter;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.community.result.CommunityPageResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageAdapter;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.community.result.CommunityListResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityServiceImpl implements CommunityService {

    private static final Logger LOGGER = LogManager.getLogger(CommunityServiceImpl.class);
    private static final String RESOURCE_TYPE = "Community";
    private static final String COMMUNITY_NOT_FOUND = "COMMUNITY_NOT_FOUND";
    private static final String COMMUNITY_CODE_CONFLICT = "COMMUNITY_CODE_CONFLICT";
    private static final String COMMUNITY_HAS_ACTIVE_BUILDINGS = "COMMUNITY_HAS_ACTIVE_BUILDINGS";

    private final CommunityMapper communityMapper;
    private final BuildingMapper buildingMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final CommunityConverter communityConverter;

    public CommunityServiceImpl(CommunityMapper communityMapper,
                                BuildingMapper buildingMapper,
                                AuditService auditService,
                                ObjectMapper objectMapper,
                                CommunityConverter communityConverter) {
        this.communityMapper = communityMapper;
        this.buildingMapper = buildingMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.communityConverter = communityConverter;
    }

    @Override
    @Transactional
    public CommunityDetailResult create(CreateCommunityCommand command) {
        String normalizedCode = normalizeCode(command.communityCode());
        checkCodeConflict(null, normalizedCode);

        CommunityEntity entity = new CommunityEntity();
        entity.setCommunityCode(normalizedCode);
        entity.setCommunityName(command.communityName());
        entity.setAdministrativeRegion(command.administrativeRegion());
        entity.setAddress(command.address());
        entity.setConstructionPeriod(command.constructionPeriod());
        entity.setBuildingCount(0);
        entity.setHouseholdCount(command.householdCount());
        entity.setResidentCount(command.residentCount());
        entity.setStatus(command.status() != null ? command.status() : "ACTIVE");
        entity.setExtraAttributes(toJsonNode(command.extraAttributes()));
        entity.setRemark(command.remark());
        entity.setVersion(0L);

        communityMapper.insert(entity);
        auditService.recordSuccess(AuditOperation.success(
                "CREATE", RESOURCE_TYPE, entity.getId(), null, entity,
                List.of(), "创建小区"));
        return communityConverter.toDetailResult(entity);
    }

    @Override
    public PageResult<CommunityListResult> page(String keyword, String administrativeRegion,
                                      String status, ApiPageRequest pageRequest, String sort) {
        Page<CommunityEntity> pageParam = PageAdapter.toMyBatisPage(pageRequest);
        LambdaQueryWrapper<CommunityEntity> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword + "%";
            wrapper.and(w -> w
                    .like(CommunityEntity::getCommunityCode, like)
                    .or().like(CommunityEntity::getCommunityName, like)
                    .or().like(CommunityEntity::getAddress, like));
        }
        if (administrativeRegion != null && !administrativeRegion.isBlank()) {
            wrapper.eq(CommunityEntity::getAdministrativeRegion, administrativeRegion);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(CommunityEntity::getStatus, status);
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
            boolean asc = parts.length < 2 || "asc".equalsIgnoreCase(parts[1]);
            switch (field) {
                case "communityCode" -> wrapper.orderBy(true, asc, CommunityEntity::getCommunityCode);
                case "communityName" -> wrapper.orderBy(true, asc, CommunityEntity::getCommunityName);
                case "createdAt" -> wrapper.orderBy(true, asc, CommunityEntity::getCreatedAt);
                case "updatedAt" -> wrapper.orderBy(true, asc, CommunityEntity::getUpdatedAt);
                default -> throw new InvalidRequestException(
                        "INVALID_SORT_FIELD", "不支持的小区排序字段: " + field);
            }
        } else {
            wrapper.orderByDesc(CommunityEntity::getCreatedAt);
        }

        Page<CommunityEntity> result = communityMapper.selectPage(pageParam, wrapper);
        return PageAdapter.toApiPage(result, communityConverter::toListResult);
    }

    @Override
    public CommunityDetailResult get(UUID communityId) {
        return communityConverter.toDetailResult(getEntity(communityId));
    }

    /**
     * 在持久层内部读取活动小区实体，不向 Service 接口或 Controller 暴露。
     *
     * @param communityId 小区标识
     * @return 活动小区持久化实体
     */
    private CommunityEntity getEntity(UUID communityId) {
        CommunityEntity entity = communityMapper.selectById(communityId);
        if (entity == null) {
            throw new ResourceNotFoundException(COMMUNITY_NOT_FOUND,
                    "小区不存在: " + communityId);
        }
        return entity;
    }

    @Override
    @Transactional
    public CommunityDetailResult update(UUID communityId, UpdateCommunityCommand command) {
        CommunityEntity entity = getEntity(communityId);

        if (!command.version().equals(entity.getVersion())) {
            throw new ResourceConflictException(
                    "RESOURCE_VERSION_CONFLICT", "小区已被其他请求修改，请刷新后重试");
        }

        JsonNode beforeData = objectMapper.valueToTree(entity);

        if (command.communityCode() != null) {
            String normalizedCode = normalizeCode(command.communityCode());
            checkCodeConflict(communityId, normalizedCode);
            entity.setCommunityCode(normalizedCode);
        }

        if (command.communityName() != null) {
            entity.setCommunityName(command.communityName());
        }
        if (command.administrativeRegion() != null) {
            entity.setAdministrativeRegion(command.administrativeRegion());
        }
        if (command.address() != null) {
            entity.setAddress(command.address());
        }
        if (command.constructionPeriod() != null) {
            entity.setConstructionPeriod(command.constructionPeriod());
        }
        if (command.householdCount() != null) {
            entity.setHouseholdCount(command.householdCount());
        }
        if (command.residentCount() != null) {
            entity.setResidentCount(command.residentCount());
        }
        if (command.status() != null) {
            entity.setStatus(command.status());
        }
        if (command.extraAttributes() != null) {
            entity.setExtraAttributes(toJsonNode(command.extraAttributes()));
        }
        if (command.remark() != null) {
            entity.setRemark(command.remark());
        }

        int updatedRows = communityMapper.updateById(entity);
        if (updatedRows == 0) {
            throw new ResourceConflictException(
                    "RESOURCE_VERSION_CONFLICT", "小区已被其他请求修改，请刷新后重试");
        }
        auditService.recordSuccess(AuditOperation.success(
                "UPDATE", RESOURCE_TYPE, communityId, beforeData, entity,
                changedCommunityFields(command), "修改小区"));
        return communityConverter.toDetailResult(entity);
    }

    @Override
    @Transactional
    public void delete(UUID communityId) {
        CommunityEntity entity = getEntity(communityId);

        LambdaQueryWrapper<BuildingEntity> buildingWrapper = new LambdaQueryWrapper<>();
        buildingWrapper.eq(BuildingEntity::getCommunityId, communityId);
        Long count = buildingMapper.selectCount(buildingWrapper);
        if (count != null && count > 0) {
            throw new ResourceConflictException(COMMUNITY_HAS_ACTIVE_BUILDINGS,
                    "小区存在有效楼栋，无法删除: " + communityId);
        }

        communityMapper.deleteById(communityId);
        auditService.recordSuccess(AuditOperation.success(
                "DELETE", RESOURCE_TYPE, communityId, entity, null,
                List.of("deletedAt"), "删除小区"));
    }

    private void checkCodeConflict(UUID excludeId, String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        LambdaQueryWrapper<CommunityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CommunityEntity::getCommunityCode, code);
        CommunityEntity existing = communityMapper.selectOne(wrapper);
        if (existing != null && (excludeId == null || !excludeId.equals(existing.getId()))) {
            throw new ResourceConflictException(COMMUNITY_CODE_CONFLICT,
                    "小区编码已存在: " + code);
        }
    }

    private JsonNode toJsonNode(Object obj) {
        return obj == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(obj);
    }

    /**
     * 统一规范化小区编码，防止仅大小写或首尾空格不同的重复编码。
     *
     * @param code 客户端提交编码
     * @return 去除首尾空格并转为大写的编码
     */
    private String normalizeCode(String code) {
        String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.isEmpty()) {
            throw new InvalidRequestException("COMMUNITY_CODE_INVALID", "小区编码不能为空");
        }
        if (!normalizedCode.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new InvalidRequestException(
                    "COMMUNITY_CODE_INVALID", "小区编码只能包含大写字母、数字、点、下划线和连字符");
        }
        return normalizedCode;
    }

    /**
     * 计算小区更新请求中的有值字段，供统一审计记录 changedFields。
     *
     * @param request 小区更新请求
     * @return 实际提交的业务字段名
     */
    private List<String> changedCommunityFields(UpdateCommunityCommand command) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (command.communityCode() != null) fields.add("communityCode");
        if (command.communityName() != null) fields.add("communityName");
        if (command.administrativeRegion() != null) fields.add("administrativeRegion");
        if (command.address() != null) fields.add("address");
        if (command.constructionPeriod() != null) fields.add("constructionPeriod");
        if (command.householdCount() != null) fields.add("householdCount");
        if (command.residentCount() != null) fields.add("residentCount");
        if (command.status() != null) fields.add("status");
        if (command.extraAttributes() != null) fields.add("extraAttributes");
        if (command.remark() != null) fields.add("remark");
        return List.copyOf(fields);
    }
}
