package org.urbansafe.priority.evidence.service;

import java.util.UUID;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.evidence.command.CreateEvidenceCommand;
import org.urbansafe.priority.evidence.command.UpdateEvidenceCommand;
import org.urbansafe.priority.evidence.result.EvidenceDetailResult;

public interface BuildingEvidenceService {

    EvidenceDetailResult createBuildingEvidence(UUID buildingId, CreateEvidenceCommand request);

    PageResult<EvidenceDetailResult> listBuildingEvidence(UUID buildingId, ApiPageRequest pageRequest);

    EvidenceDetailResult getBuildingEvidence(UUID evidenceId);

    EvidenceDetailResult updateBuildingEvidence(UUID evidenceId, UpdateEvidenceCommand request);

    void deleteBuildingEvidence(UUID evidenceId);
}
