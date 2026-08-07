package org.urbansafe.priority.audit.result;

import java.util.List;
import org.urbansafe.priority.common.pagination.PageResult;

/**
 * 表示审计日志查询的内部不可变分页结果。
 *
 * @param content 当前页审计日志列表
 * @param page 当前 API 零基页号
 * @param size 当前页页面大小
 * @param totalElements 查询命中的总记录数
 * @param totalPages 查询结果总页数
 */
public record OperationLogPageResult(
        List<OperationLogResult> content,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    /**
     * 从统一分页结果创建审计业务分页结果，保证零基页号不在业务代码中重复换算。
     *
     * @param pageResult 公共分页适配层生成的零基分页结果
     * @return 仅承载审计日志的内部分页结果
     */
    public static OperationLogPageResult from(PageResult<OperationLogResult> pageResult) {
        // 将公共分页内容复制为不可变集合，避免持久层集合被后续调用修改。
        List<OperationLogResult> content = List.copyOf(pageResult.content());
        return new OperationLogPageResult(
                content,
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages());
    }

    /**
     * 复制内容集合，确保分页结果及其条目列表在 Service 边界外保持不可变。
     */
    public OperationLogPageResult {
        // 当前页记录是审计证据快照，禁止 Controller 修改或重排记录。
        content = List.copyOf(content);
    }
}
