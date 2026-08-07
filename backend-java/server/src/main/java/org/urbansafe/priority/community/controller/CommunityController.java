package org.urbansafe.priority.community.controller;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageResult;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.common.security.CommunityAccessScope;
import org.urbansafe.priority.common.security.ScopedArchiveQueryService;
import org.urbansafe.priority.community.converter.CommunityConverter;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.community.result.CommunityListResult;
import org.urbansafe.priority.community.service.CommunityService;
import org.urbansafe.priority.model.api.CommunityApi;
import org.urbansafe.priority.model.dto.CommunityPageResponse;
import org.urbansafe.priority.model.dto.CommunityPageSuccessResponse;
import org.urbansafe.priority.model.dto.CommunitySuccessResponse;
import org.urbansafe.priority.model.dto.CreateCommunityRequest;
import org.urbansafe.priority.model.dto.DeletionResponse;
import org.urbansafe.priority.model.dto.DeletionSuccessResponse;
import org.urbansafe.priority.model.dto.PageMetadata;
import org.urbansafe.priority.model.dto.UpdateCommunityRequest;

@RestController
public class CommunityController implements CommunityApi {

    private final CommunityService communityService;
    private final CommunityConverter communityConverter;
    private final BusinessAccessService accessService;
    private final ScopedArchiveQueryService scopedQueryService;

    public CommunityController(
            CommunityService communityService,
            CommunityConverter communityConverter,
            BusinessAccessService accessService,
            ScopedArchiveQueryService scopedQueryService) {
        this.communityService = communityService;
        this.communityConverter = communityConverter;
        this.accessService = accessService;
        this.scopedQueryService = scopedQueryService;
    }

    @Override
    @PreAuthorize(BusinessAccessService.COMMUNITY_CREATE_DELETE_ROLES)
    public ResponseEntity<CommunitySuccessResponse> createCommunity(
            CreateCommunityRequest createCommunityRequest) {
        accessService.assertCanCreateCommunity();
        CommunityDetailResult result = communityService.create(
                communityConverter.toCommand(createCommunityRequest));

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CommunitySuccessResponse response = new CommunitySuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(communityConverter.toResponse(result));
        response.setError(null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<CommunityPageSuccessResponse> listCommunities(
            String keyword,
            String administrativeRegion,
            String status,
            Integer page,
            Integer size,
            String sort) {
        ApiPageRequest pageRequest = ApiPageRequest.of(page, size);
        CommunityAccessScope scope = accessService.currentCommunityScope();
        PageResult<CommunityListResult> result = scope.global()
                ? communityService.page(
                        keyword, administrativeRegion, status, pageRequest, sort)
                : scopedQueryService.listCommunities(
                        keyword, administrativeRegion, status, pageRequest, sort, scope);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CommunityPageSuccessResponse response = new CommunityPageSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);

        CommunityPageResponse pageData = new CommunityPageResponse();
        pageData.setContent(result.content().stream()
                .map(communityConverter::toListRow)
                .toList());
        PageMetadata pageMetadata = new PageMetadata();
        pageMetadata.setPage(result.page());
        pageMetadata.setSize(result.size());
        pageMetadata.setTotalElements(result.totalElements());
        pageMetadata.setTotalPages(Math.toIntExact(result.totalPages()));
        pageData.setPage(pageMetadata);
        response.setData(pageData);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.DIRECTORY_READ_ROLES)
    public ResponseEntity<CommunitySuccessResponse> getCommunity(UUID communityId) {
        accessService.assertCanReadCommunity(communityId);
        CommunityDetailResult result = communityService.get(communityId);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CommunitySuccessResponse response = new CommunitySuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(communityConverter.toResponse(result));
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.ARCHIVE_MANAGE_ROLES)
    public ResponseEntity<CommunitySuccessResponse> updateCommunity(
            UUID communityId, UpdateCommunityRequest updateCommunityRequest) {
        accessService.assertCanManageCommunity(communityId);
        CommunityDetailResult result = communityService.update(
                communityId, communityConverter.toCommand(updateCommunityRequest));

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        CommunitySuccessResponse response = new CommunitySuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(communityConverter.toResponse(result));
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize(BusinessAccessService.COMMUNITY_CREATE_DELETE_ROLES)
    public ResponseEntity<DeletionSuccessResponse> deleteCommunity(UUID communityId) {
        accessService.assertCanDeleteCommunity(communityId);
        communityService.delete(communityId);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        DeletionSuccessResponse response = new DeletionSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setError(null);

        DeletionResponse deletionData = new DeletionResponse();
        deletionData.setResourceId(communityId);
        deletionData.setResourceType("Community");
        deletionData.setDeletedAt(metadata.timestamp());
        response.setData(deletionData);
        return ResponseEntity.ok(response);
    }
}
