package org.urbansafe.priority.building.service;

import java.util.UUID;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.command.UpdateBuildingCommand;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;

public interface BuildingService {

    BuildingDetailResult createBuilding(CreateBuildingCommand request);

    PageResult<BuildingListResult> listBuildings(UUID communityId, String keyword, ApiPageRequest pageRequest, String sort);

    BuildingDetailResult getBuilding(UUID buildingId);

    BuildingDetailResult updateBuilding(UUID buildingId, UpdateBuildingCommand request);

    void deleteBuilding(UUID buildingId);
}
