package org.urbansafe.priority.audit.result;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 表示审计日志单条记录的内部不可变结果。
 *
 * @param id 审计日志唯一标识
 * @param requestId 请求追踪编号
 * @param userId 操作用户标识
 * @param action 操作类型
 * @param resourceType 被操作资源类型
 * @param resourceId 被操作资源标识
 * @param success 操作是否成功
 * @param changeSummary 已脱敏的变更摘要
 * @param clientIp 客户端 IP 地址
 * @param operatedAt 操作发生时间
 * @param errorCode 失败时的稳定错误码
 * @param errorMessage 失败时的已脱敏错误信息
 */
public record OperationLogResult(
        UUID id,
        String requestId,
        UUID userId,
        String action,
        String resourceType,
        UUID resourceId,
        Boolean success,
        Object changeSummary,
        String clientIp,
        OffsetDateTime operatedAt,
        String errorCode,
        String errorMessage) {
}
