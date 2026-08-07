package org.urbansafe.priority.building.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.command.UpdateBuildingCommand;
import org.urbansafe.priority.building.converter.BuildingConverter;
import org.urbansafe.priority.building.result.BuildingDetailResult;
import org.urbansafe.priority.building.result.BuildingListResult;
import org.urbansafe.priority.building.service.BuildingService;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;
import org.urbansafe.priority.common.security.ScopedArchiveQueryService;
import org.urbansafe.priority.model.api.BuildingApi;
import org.urbansafe.priority.model.dto.BuildingPageResponse;
import org.urbansafe.priority.model.dto.BuildingPageSuccessResponse;
import org.urbansafe.priority.model.dto.BuildingResponse;
import org.urbansafe.priority.model.dto.BuildingSuccessResponse;
import org.urbansafe.priority.model.dto.CreateBuildingRequest;
import org.urbansafe.priority.model.dto.DeletionResponse;
import org.urbansafe.priority.model.dto.DeletionSuccessResponse;
import org.urbansafe.priority.model.dto.PageMetadata;
import org.urbansafe.priority.model.dto.UpdateBuildingRequest;

@RestController
public class BuildingController implements BuildingApi {

    private final BuildingService buildingService;
    private final BuildingConverter buildingConverter;
    private final BusinessAccessService accessService;
    private final ScopedArchiveQueryService scopedQueryService;

    public BuildingController(
            BuildingService buildingService,
            BuildingConverter buildingConverter,
            BusinessAccessService accessService,
            ScopedArchiveQueryService scopedQueryService) {
        this.buildingService = buildingService;
        this.buildingConverter = buildingConverter;
        this.accessService = accessService;
        this.scopedQueryService = scopedQueryService;
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<BuildingSuccessResponse> createBuilding(
            CreateBuildingRequest createBuildingRequest) {
        CreateBuildingCommand command = buildingConverter.toCommand(createBuildingRequest);
        accessService.assertCanCreateBuilding(command.getCommunityId());
        BuildingDetailResult data = buildingService.createBuilding(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(wrapBuilding(buildingConverter.toResponse(data)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<BuildingPageSuccessResponse> listBuildings(
            UUID communityId, String keyword, Integer page, Integer size, String sort) {
        ApiPageRequest pageRequest = ApiPageRequest.of(page, size);
        CommunityAccessScope scope = accessService.currentCommunityScope();
        PageResult<BuildingListResult> result = scope.global()
                ? buildingService.listBuildings(communityId, keyword, pageRequest, sort)
                : scopedQueryService.listBuildings(
                        communityId, keyword, pageRequest, sort, scope);

        BuildingPageResponse data = new BuildingPageResponse();
        data.setContent(result.content().stream()
                .map(buildingConverter::toListRow)
                .toList());
        data.setPage(new PageMetadata(
                result.page(),
                result.size(),
                result.totalElements(),
                Math.toIntExact(result.totalPages())));
        return ResponseEntity.ok(wrapBuildingPage(data));
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<BuildingSuccessResponse> getBuilding(UUID buildingId) {
        accessService.assertCanReadBuilding(buildingId);
        BuildingDetailResult data = buildingService.getBuilding(buildingId);
        return ResponseEntity.ok(wrapBuilding(buildingConverter.toResponse(data)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<BuildingSuccessResponse> updateBuilding(
            UUID buildingId, UpdateBuildingRequest updateBuildingRequest) {
        UpdateBuildingCommand command = buildingConverter.toCommand(updateBuildingRequest);
        if (command.getCommunityId() == null) {
            accessService.assertCanManageBuilding(buildingId);
        } else {
            accessService.assertCanMoveBuilding(buildingId, command.getCommunityId());
        }
        BuildingDetailResult data = buildingService.updateBuilding(buildingId, command);
        return ResponseEntity.ok(wrapBuilding(buildingConverter.toResponse(data)));
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<DeletionSuccessResponse> deleteBuilding(UUID buildingId) {
        accessService.assertCanManageBuilding(buildingId);
        buildingService.deleteBuilding(buildingId);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        DeletionSuccessResponse response = new DeletionSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);

        DeletionResponse data = new DeletionResponse();
        data.setResourceId(buildingId);
        data.setResourceType("Building");
        data.setDeletedAt(metadata.timestamp());
        response.setData(data);
        return ResponseEntity.ok(response);
    }

    private BuildingSuccessResponse wrapBuilding(BuildingResponse data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        BuildingSuccessResponse response = new BuildingSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return response;
    }

    private BuildingPageSuccessResponse wrapBuildingPage(BuildingPageResponse data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        BuildingPageSuccessResponse response = new BuildingPageSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);
        return response;
    }
}
