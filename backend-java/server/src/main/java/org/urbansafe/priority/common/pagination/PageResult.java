package org.urbansafe.priority.common.pagination;

import java.util.List;

/**
 * 表示统一的 API 分页响应数据。
 *
 * <p>该结果始终使用零基 {@code page}，以便所有业务接口向前端暴露一致的分页约定。
 *
 * @param content 当前页已经完成类型转换的记录列表
 * @param page 当前 API 零基页号
 * @param size 当前页请求的单页记录数
 * @param totalElements 查询命中的总记录数
 * @param totalPages 按请求页面大小计算出的总页数
 * @param <T> API 层当前页记录元素类型
 */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        long totalPages) {
}
