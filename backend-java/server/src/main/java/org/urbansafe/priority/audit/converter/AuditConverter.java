package org.urbansafe.priority.audit.converter;

import org.urbansafe.priority.audit.result.OperationLogPageResult;
import org.urbansafe.priority.audit.result.OperationLogResult;
import org.urbansafe.priority.model.dto.OperationLogPageResponse;
import org.urbansafe.priority.model.dto.OperationLogResponse;
import org.urbansafe.priority.model.dto.PageMetadata;

/**
 * 审计内部结果到 OpenAPI 生成 DTO 的唯一转换入口。
 *
 * <p>审计 Service 只产生内部结果，Controller 必须通过本类创建日志、分页元数据及分页响应 DTO。
 */
public final class AuditConverter {

    /**
     * 隐藏工具类构造器，禁止创建无状态转换器实例。
     */
    private AuditConverter() {
        // 所有转换方法均为静态方法，不需要实例状态。
    }

    /**
     * 将内部审计日志结果转换为 OpenAPI 单条审计日志响应。
     *
     * @param operationLogResult 审计 Service 返回的不可变日志结果
     * @return 面向客户端的 OpenAPI 审计日志响应
     */
    public static OperationLogResponse toOperationLogResponse(OperationLogResult operationLogResult) {
        // 创建 OpenAPI 日志对象并逐项映射已脱敏、已标准化的审计字段。
        OperationLogResponse operationLogResponse = new OperationLogResponse();
        operationLogResponse.setId(operationLogResult.id());
        operationLogResponse.setRequestId(operationLogResult.requestId());
        operationLogResponse.setUserId(operationLogResult.userId());
        operationLogResponse.setAction(operationLogResult.action());
        operationLogResponse.setResourceType(operationLogResult.resourceType());
        operationLogResponse.setResourceId(operationLogResult.resourceId());
        operationLogResponse.setSuccess(operationLogResult.success());
        operationLogResponse.setChangeSummary(operationLogResult.changeSummary());
        operationLogResponse.setClientIp(operationLogResult.clientIp());
        operationLogResponse.setOperatedAt(operationLogResult.operatedAt());
        operationLogResponse.setErrorCode(operationLogResult.errorCode());
        operationLogResponse.setErrorMessage(operationLogResult.errorMessage());
        return operationLogResponse;
    }

    /**
     * 将内部审计分页结果转换为保持 API 零基页号的 OpenAPI 分页响应。
     *
     * @param pageResult 审计 Service 返回的统一分页结果
     * @return 内容与元数据均为 OpenAPI DTO 的分页响应
     */
    public static OperationLogPageResponse toOperationLogPageResponse(OperationLogPageResult pageResult) {
        // 将当前页内部审计记录转换为 OpenAPI 日志响应列表。
        var content = pageResult.content().stream()
                .map(AuditConverter::toOperationLogResponse)
                .toList();
        // 直接使用公共分页适配层已经恢复的 API 零基页号，不在 Converter 再次换算。
        PageMetadata pageMetadata = new PageMetadata(
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                Math.toIntExact(pageResult.totalPages()));
        // 创建 OpenAPI 分页响应，并一次性写入转换后的内容和元数据。
        OperationLogPageResponse operationLogPageResponse = new OperationLogPageResponse();
        operationLogPageResponse.setContent(content);
        operationLogPageResponse.setPage(pageMetadata);
        return operationLogPageResponse;
    }
}
