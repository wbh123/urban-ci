package org.urbansafe.priority.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.persistence.entity.BuildingEvidenceEntity;
import org.urbansafe.priority.persistence.mapper.BuildingEvidenceMapper;
import org.urbansafe.priority.evidence.service.BuildingEvidenceService;
import org.urbansafe.priority.evidence.command.CreateEvidenceCommand;
import org.urbansafe.priority.evidence.command.UpdateEvidenceCommand;
import org.urbansafe.priority.evidence.converter.EvidenceConverter;
import org.urbansafe.priority.evidence.result.EvidenceDetailResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageAdapter;
import org.urbansafe.priority.common.pagination.PageResult;

@Service
public class BuildingEvidenceServiceImpl implements BuildingEvidenceService {

    private static final Logger LOGGER = LogManager.getLogger(BuildingEvidenceServiceImpl.class);

    private final BuildingEvidenceMapper buildingEvidenceMapper;
    private final BuildingMapper buildingMapper;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final EvidenceConverter evidenceConverter;

    public BuildingEvidenceServiceImpl(
            BuildingEvidenceMapper buildingEvidenceMapper,
            BuildingMapper buildingMapper,
            AuditService auditService,
            ObjectMapper objectMapper,
            Clock clock, EvidenceConverter evidenceConverter) {
        this.buildingEvidenceMapper = buildingEvidenceMapper;
        this.buildingMapper = buildingMapper;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.evidenceConverter = evidenceConverter;
    }

    @Override
    @Transactional
    public EvidenceDetailResult createBuildingEvidence(UUID buildingId, CreateEvidenceCommand request) {
        requireActiveBuilding(buildingId);

        BuildingEvidenceEntity entity = new BuildingEvidenceEntity();
        entity.setBuildingId(buildingId);
        entity.setEvidenceType(request.getEvidenceType());
        entity.setTitle(request.getTitle());
        entity.setDescription(request.getDescription());
        entity.setOccurredAt(request.getOccurredAt());
        entity.setSource(request.getSource());
        entity.setReliabilityLevel(request.getReliabilityLevel());
        entity.setEvidenceData(toJsonNode(request.getEvidenceData()));
        entity.setCreatedBy(CurrentUser.getUserId());
        entity.setVersion(0L);

        buildingEvidenceMapper.insert(entity);

        auditService.recordSuccess(AuditOperation.success(
                "CREATE", "BuildingEvidence", entity.getId(), null, entity,
                List.of(), "创建楼栋证据"));

        return evidenceConverter.toDetailResult(entity);
    }

    @Override
    public PageResult<EvidenceDetailResult> listBuildingEvidence(UUID buildingId, ApiPageRequest pageRequest) {
        requireActiveBuilding(buildingId);
        LambdaQueryWrapper<BuildingEvidenceEntity> wrapper = new LambdaQueryWrapper<BuildingEvidenceEntity>()
                .eq(BuildingEvidenceEntity::getBuildingId, buildingId)
                .orderByDesc(BuildingEvidenceEntity::getCreatedAt);

        Page<BuildingEvidenceEntity> pageResult = buildingEvidenceMapper.selectPage(
                PageAdapter.toMyBatisPage(pageRequest), wrapper);
        return PageAdapter.toApiPage(pageResult, evidenceConverter::toDetailResult);
    }

    @Override
    public EvidenceDetailResult getBuildingEvidence(UUID evidenceId) {
        BuildingEvidenceEntity entity = buildingEvidenceMapper.selectById(evidenceId);
        if (entity == null) {
            throw new ResourceNotFoundException("EVIDENCE_NOT_FOUND", "证据不存在");
        }

        BuildingEntity building = buildingMapper.selectById(entity.getBuildingId());
        if (building == null) {
            throw new ResourceNotFoundException("BUILDING_DELETED", "父楼栋已删除");
        }

        return evidenceConverter.toDetailResult(entity);
    }

    @Override
    @Transactional
    public EvidenceDetailResult updateBuildingEvidence(UUID evidenceId, UpdateEvidenceCommand request) {
        BuildingEvidenceEntity entity = buildingEvidenceMapper.selectById(evidenceId);
        if (entity == null) {
            throw new ResourceNotFoundException("EVIDENCE_NOT_FOUND", "证据不存在");
        }

        requireActiveBuilding(entity.getBuildingId());
        if (!request.getVersion().equals(entity.getVersion())) {
            throw new ResourceConflictException(
                    "RESOURCE_VERSION_CONFLICT", "证据已被其他请求修改，请刷新后重试");
        }

        JsonNode beforeData = objectMapper.valueToTree(entity);

        if (request.getEvidenceType() != null) entity.setEvidenceType(request.getEvidenceType());
        if (request.getTitle() != null) entity.setTitle(request.getTitle());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getOccurredAt() != null) entity.setOccurredAt(request.getOccurredAt());
        if (request.getSource() != null) entity.setSource(request.getSource());
        if (request.getReliabilityLevel() != null)
            entity.setReliabilityLevel(request.getReliabilityLevel());
        if (request.getEvidenceData() != null) entity.setEvidenceData(toJsonNode(request.getEvidenceData()));

        int updatedRows = buildingEvidenceMapper.updateById(entity);
        if (updatedRows == 0) {
            throw new ResourceConflictException(
                    "RESOURCE_VERSION_CONFLICT", "证据已被其他请求修改，请刷新后重试");
        }

        auditService.recordSuccess(AuditOperation.success(
                "UPDATE", "BuildingEvidence", evidenceId, beforeData, entity,
                changedEvidenceFields(request), "修改楼栋证据"));

        return evidenceConverter.toDetailResult(entity);
    }

    @Override
    @Transactional
    public void deleteBuildingEvidence(UUID evidenceId) {
        BuildingEvidenceEntity entity = buildingEvidenceMapper.selectById(evidenceId);
        if (entity == null) {
            throw new ResourceNotFoundException("EVIDENCE_NOT_FOUND", "证据不存在");
        }

        requireActiveBuilding(entity.getBuildingId());

        buildingEvidenceMapper.deleteById(evidenceId);

        auditService.recordSuccess(AuditOperation.success(
                "DELETE", "BuildingEvidence", evidenceId, entity, null,
                List.of("deletedAt"), "删除楼栋证据"));

    }

    private JsonNode toJsonNode(Object obj) {
        return obj == null ? objectMapper.createObjectNode() : objectMapper.valueToTree(obj);
    }

    /**
     * 校验父楼栋存在且未被逻辑删除；MyBatis-Plus 默认查询会自动排除 deleted_at 非空记录。
     *
     * @param buildingId 父楼栋标识
     * @return 活动楼栋实体
     */
    private BuildingEntity requireActiveBuilding(UUID buildingId) {
        BuildingEntity building = buildingMapper.selectById(buildingId);
        if (building == null) {
            throw new ResourceNotFoundException("BUILDING_NOT_FOUND", "父楼栋不存在或已删除");
        }
        return building;
    }

    /**
     * 计算证据更新请求中实际提交的业务字段。
     *
     * @param request 证据更新请求
     * @return 变更字段名列表
     */
    private List<String> changedEvidenceFields(UpdateEvidenceCommand request) {
        java.util.ArrayList<String> fields = new java.util.ArrayList<>();
        if (request.getEvidenceType() != null) fields.add("evidenceType");
        if (request.getTitle() != null) fields.add("title");
        if (request.getDescription() != null) fields.add("description");
        if (request.getOccurredAt() != null) fields.add("occurredAt");
        if (request.getSource() != null) fields.add("source");
        if (request.getReliabilityLevel() != null) fields.add("reliabilityLevel");
        if (request.getEvidenceData() != null) fields.add("evidenceData");
        return List.copyOf(fields);
    }
}
