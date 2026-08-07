package org.urbansafe.priority.audit.controller;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.urbansafe.priority.audit.converter.AuditConverter;
import org.urbansafe.priority.audit.result.OperationLogPageResult;
import org.urbansafe.priority.audit.result.OperationLogResult;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.AuditOperationApi;
import org.urbansafe.priority.model.dto.OperationLogSuccessResponse;
import org.urbansafe.priority.model.dto.OperationLogPageSuccessResponse;
import org.urbansafe.priority.model.dto.OperationLogPageResponse;
import org.urbansafe.priority.model.dto.OperationLogResponse;

@RestController
public class AuditOperationController implements AuditOperationApi {

    private final AuditService auditService;

    public AuditOperationController(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OperationLogPageSuccessResponse> listOperationLogs(
            String requestId,
            String action,
            String resourceType,
            UUID resourceId,
            Integer page,
            Integer size) {

        // 将可空 HTTP 查询参数归一化为统一的零基分页请求。
        ApiPageRequest pageRequest = ApiPageRequest.of(page, size);
        OperationLogPageResult pageResult = auditService.page(
                requestId,
                action,
                resourceType,
                resourceId,
                pageRequest);
        OperationLogPageResponse pageData = AuditConverter.toOperationLogPageResponse(pageResult);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        OperationLogPageSuccessResponse response = new OperationLogPageSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(pageData);
        response.setError(null);

        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OperationLogSuccessResponse> getOperationLog(UUID operationId) {
        OperationLogResult logResult = auditService.get(operationId);
        OperationLogResponse logData = AuditConverter.toOperationLogResponse(logResult);

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        OperationLogSuccessResponse response = new OperationLogSuccessResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(logData);
        response.setError(null);

        return ResponseEntity.ok(response);
    }
}
