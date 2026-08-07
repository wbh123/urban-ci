package org.urbansafe.priority.audit.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.persistence.entity.OperationLogEntity;
import org.urbansafe.priority.persistence.repository.OperationLogRepository;
import org.urbansafe.priority.audit.model.AuditContext;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.result.OperationLogPageResult;
import org.urbansafe.priority.audit.result.OperationLogResult;
import org.urbansafe.priority.audit.service.AuditDataSanitizer;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.common.pagination.PageAdapter;
import org.urbansafe.priority.common.pagination.PageResult;

@Service
public class AuditServiceImpl implements AuditService {

    private final OperationLogRepository operationLogRepository;
    private final AuditDataSanitizer auditDataSanitizer;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 创建统一审计服务。
     *
     * @param operationLogRepository 审计日志仓库
     * @param auditDataSanitizer 统一递归脱敏器
     * @param objectMapper 项目统一 JSON 映射器
     * @param clock 可测试的统一时钟
     */
    public AuditServiceImpl(
            OperationLogRepository operationLogRepository,
            AuditDataSanitizer auditDataSanitizer,
            ObjectMapper objectMapper,
            Clock clock) {
        this.operationLogRepository = operationLogRepository;
        this.auditDataSanitizer = auditDataSanitizer;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 在调用方事务内追加成功审计；该方法不吞异常，确保审计失败时业务写入一并回滚。
     *
     * @param operation 业务操作描述
     */
    @Override
    @Transactional
    public void recordSuccess(AuditOperation operation) {
        AuditContext context = AuditContext.capture();
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(context.userId());
        entity.setOperationType(operation.operationType());
        entity.setResourceType(operation.resourceType());
        entity.setResourceId(operation.resourceId());
        entity.setRequestId(context.requestId());
        entity.setClientIp(context.clientIp());
        entity.setSuccess(true);
        entity.setOperationDetail(auditDataSanitizer.sanitize(
                java.util.Map.of("summary", operation.summary() == null ? "" : operation.summary())));
        entity.setBeforeData(auditDataSanitizer.sanitize(operation.beforeData()));
        entity.setAfterData(auditDataSanitizer.sanitize(operation.afterData()));
        entity.setChangedFields(objectMapper.valueToTree(operation.changedFields()));
        entity.setOperatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        operationLogRepository.insert(entity);
    }

    /**
     * 在独立事务中追加失败审计，使主事务回滚不会删除失败证据。
     *
     * @param operation 业务操作描述
     * @param errorCode 稳定错误码
     * @param errorMessage 已脱敏的错误摘要
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(AuditOperation operation, String errorCode, String errorMessage) {
        AuditContext context = AuditContext.capture();
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(context.userId());
        entity.setOperationType(operation.operationType());
        entity.setResourceType(operation.resourceType());
        entity.setResourceId(operation.resourceId());
        entity.setRequestId(context.requestId());
        entity.setClientIp(context.clientIp());
        entity.setSuccess(false);
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(errorMessage);
        entity.setOperationDetail(auditDataSanitizer.sanitize(
                java.util.Map.of("summary", operation.summary() == null ? "" : operation.summary())));
        entity.setBeforeData(auditDataSanitizer.sanitize(operation.beforeData()));
        entity.setAfterData(auditDataSanitizer.sanitize(operation.afterData()));
        entity.setChangedFields(objectMapper.valueToTree(operation.changedFields()));
        entity.setOperatedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        operationLogRepository.insert(entity);
    }

    @Override
    public OperationLogPageResult page(
            String requestId,
            String action,
            String resourceType,
            UUID resourceId,
            ApiPageRequest pageRequest) {

        // 统一适配层在此处将 API 零基页号转换为 MyBatis-Plus 一基分页对象。
        Page<OperationLogEntity> myBatisPage = PageAdapter.toMyBatisPage(pageRequest);
        Page<OperationLogEntity> result = operationLogRepository.page(
                requestId, action, resourceType, resourceId, myBatisPage);
        // 统一适配层在此处将持久层一基页号还原为 API 零基页号并转换记录。
        PageResult<OperationLogResult> apiPageResult = PageAdapter.toApiPage(result, this::toResult);
        return OperationLogPageResult.from(apiPageResult);
    }

    @Override
    public OperationLogResult get(UUID operationId) {
        OperationLogEntity entity = operationLogRepository.findById(operationId);
        if (entity == null) {
            throw new ResourceNotFoundException("OPERATION_LOG_NOT_FOUND", "操作日志不存在");
        }
        return toResult(entity);
    }

    /**
     * 将持久化审计实体转换为不包含 OpenAPI DTO 的内部审计结果。
     *
     * @param entity 已从审计仓库读取的持久化实体
     * @return 供 Controller 转换器使用的不可变内部审计结果
     */
    private OperationLogResult toResult(OperationLogEntity entity) {
        // 将持久化字段完整复制到内部结果，保持审计数据的只读证据快照语义。
        return new OperationLogResult(
                entity.getId(),
                entity.getRequestId(),
                entity.getUserId(),
                entity.getOperationType(),
                entity.getResourceType(),
                entity.getResourceId(),
                entity.getSuccess(),
                entity.getOperationDetail(),
                entity.getClientIp(),
                entity.getOperatedAt(),
                entity.getErrorCode(),
                entity.getErrorMessage());
    }
}
