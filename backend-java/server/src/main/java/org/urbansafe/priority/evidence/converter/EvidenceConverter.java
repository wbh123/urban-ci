package org.urbansafe.priority.evidence.converter;

import org.springframework.stereotype.Component;
import org.urbansafe.priority.evidence.command.CreateEvidenceCommand;
import org.urbansafe.priority.evidence.command.UpdateEvidenceCommand;
import org.urbansafe.priority.evidence.result.EvidenceDetailResult;
import org.urbansafe.priority.model.dto.CreateEvidenceRequest;
import org.urbansafe.priority.model.dto.EvidenceResponse;
import org.urbansafe.priority.model.dto.UpdateEvidenceRequest;
import org.urbansafe.priority.persistence.entity.BuildingEvidenceEntity;

/** 集中执行证据 OpenAPI DTO、内部对象和持久化实体的转换。 */
@Component
public class EvidenceConverter {
    /** 将创建 DTO 转换为内部命令。 */
    public CreateEvidenceCommand toCommand(CreateEvidenceRequest request) { return new CreateEvidenceCommand(request.getEvidenceType().getValue(), request.getTitle(), request.getDescription(), request.getOccurredAt(), request.getSource(), request.getReliabilityLevel() == null ? "UNVERIFIED" : request.getReliabilityLevel().getValue(), request.getEvidenceData()); }
    /** 将更新 DTO 转换为内部命令。 */
    public UpdateEvidenceCommand toCommand(UpdateEvidenceRequest request) { return new UpdateEvidenceCommand(request.getVersion(), request.getEvidenceType() == null ? null : request.getEvidenceType().getValue(), request.getTitle(), request.getDescription(), request.getOccurredAt(), request.getSource(), request.getReliabilityLevel() == null ? null : request.getReliabilityLevel().getValue(), request.getEvidenceData()); }
    /** 将实体转换为内部详情结果。 */
    public EvidenceDetailResult toDetailResult(BuildingEvidenceEntity entity) { return new EvidenceDetailResult(entity.getId(), entity.getBuildingId(), entity.getEvidenceType(), entity.getTitle(), entity.getDescription(), entity.getOccurredAt(), entity.getSource(), entity.getReliabilityLevel(), entity.getEvidenceData(), entity.getCreatedBy(), entity.getCreatedAt(), entity.getUpdatedAt(), entity.getVersion()); }
    /** 将内部详情结果转换为生成的响应 DTO。 */
    public EvidenceResponse toResponse(EvidenceDetailResult result) { EvidenceResponse response = new EvidenceResponse(); response.setId(result.id()); response.setBuildingId(result.buildingId()); response.setEvidenceType(result.evidenceType()); response.setTitle(result.title()); response.setDescription(result.description()); response.setOccurredAt(result.occurredAt()); response.setSource(result.source()); response.setReliabilityLevel(result.reliabilityLevel()); response.setEvidenceData(result.evidenceData()); response.setCreatedBy(result.createdBy()); response.setCreatedAt(result.createdAt()); response.setUpdatedAt(result.updatedAt()); response.setVersion(result.version()); return response; }
}
