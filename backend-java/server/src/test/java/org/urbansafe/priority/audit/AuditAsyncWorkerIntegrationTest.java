package org.urbansafe.priority.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/**
 * 异步执行工作器线程没有 Web 请求上下文：{@link RequestContext#getClientIp()} 返回空字符串，
 * 写入 {@code audit.operation_log.client_ip INET} 必须安全落为 NULL，而不是抛出
 * {@code invalid input syntax for type inet}。真实联调第七阶段在错误场景中复现了该缺陷：
 * 空 clientIp 导致审计写入失败，进而把执行任务的终态错误标记为 AI_EXECUTION_UNEXPECTED。
 */
class AuditAsyncWorkerIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void recordSuccessFromNonWebThreadShouldWriteNullClientIp() {
        RequestContext.clear();
        UUID inferenceId = UUID.randomUUID();
        AuditOperation operation = AuditOperation.success(
                "AI_INFERENCE_CREATE",
                "AiInferenceTask",
                inferenceId,
                null,
                Map.of("inferenceId", inferenceId, "assetId", UUID.randomUUID()),
                List.of(),
                "创建推理任务");

        // 当前实现把空字符串写入 INET 列并抛异常；修复后必须无异常完成。
        assertThatCode(() -> auditService.recordSuccess(operation))
                .doesNotThrowAnyException();

        Integer clientIpCount = jdbc.queryForObject("""
                SELECT count(*) FROM audit.operation_log
                WHERE operation_type = 'AI_INFERENCE_CREATE'
                  AND resource_id = ?
                  AND client_ip IS NULL
                """, Integer.class, inferenceId);
        assertThat(clientIpCount).isEqualTo(1);
    }

    @Test
    void recordFailureFromNonWebThreadShouldWriteNullClientIp() {
        RequestContext.clear();
        UUID inferenceId = UUID.randomUUID();
        AuditOperation operation = AuditOperation.success(
                "AI_INFERENCE_CREATE",
                "AiInferenceTask",
                inferenceId,
                null,
                Map.of("inferenceId", inferenceId, "assetId", UUID.randomUUID()),
                List.of(),
                "创建推理任务");

        assertThatCode(() -> auditService.recordFailure(
                operation, "AI_PROVIDER_UNAVAILABLE", "provider unavailable"))
                .doesNotThrowAnyException();

        Integer clientIpCount = jdbc.queryForObject("""
                SELECT count(*) FROM audit.operation_log
                WHERE operation_type = 'AI_INFERENCE_CREATE'
                  AND resource_id = ?
                  AND success = false
                  AND error_code = 'AI_PROVIDER_UNAVAILABLE'
                  AND client_ip IS NULL
                """, Integer.class, inferenceId);
        assertThat(clientIpCount).isEqualTo(1);
    }
}
