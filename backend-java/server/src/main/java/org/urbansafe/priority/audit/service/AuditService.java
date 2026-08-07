package org.urbansafe.priority.audit.service;

import java.util.UUID;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.result.OperationLogPageResult;
import org.urbansafe.priority.audit.result.OperationLogResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;

public interface AuditService {

    /**
     * 在当前业务事务内记录成功操作；写入失败必须向上传播以回滚业务修改。
     *
     * @param operation 与持久化实现解耦的操作描述
     */
    void recordSuccess(AuditOperation operation);

    /**
     * 使用独立事务记录失败操作，避免调用方事务回滚后丢失失败证据。
     *
     * @param operation 与持久化实现解耦的操作描述
     * @param errorCode 稳定业务错误码
     * @param errorMessage 不含敏感输入的错误摘要
     */
    void recordFailure(AuditOperation operation, String errorCode, String errorMessage);

    /**
     * 按筛选条件查询审计日志，并使用统一零基分页请求完成分页适配。
     *
     * @param requestId 可选请求追踪编号
     * @param action 可选操作类型
     * @param resourceType 可选资源类型
     * @param resourceId 可选资源唯一标识
     * @param pageRequest 已校验的 API 零基分页请求
     * @return 与 OpenAPI DTO 解耦的内部审计分页结果
     */
    OperationLogPageResult page(
            String requestId,
            String action,
            String resourceType,
            UUID resourceId,
            ApiPageRequest pageRequest);

    /**
     * 查询单条审计日志证据。
     *
     * @param operationId 审计日志唯一标识
     * @return 与 OpenAPI DTO 解耦的内部审计日志结果
     */
    OperationLogResult get(UUID operationId);
}
