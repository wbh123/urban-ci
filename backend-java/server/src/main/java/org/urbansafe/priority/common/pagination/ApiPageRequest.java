package org.urbansafe.priority.common.pagination;

import org.urbansafe.priority.common.exception.InvalidRequestException;

/**
 * 表示外部 HTTP API 使用的零基分页请求参数。
 *
 * <p>该类型只承载接口语义：{@code page} 从零开始、{@code size} 表示单页记录数。
 * MyBatis-Plus 的一基页号转换必须由 {@link PageAdapter} 集中处理，避免业务模块重复加一或漏加一。
 *
 * @param page API 零基页号，必须大于等于零
 * @param size 每页记录数，必须大于零
 */
public record ApiPageRequest(int page, int size) {

    /** 未提供 API 页号时采用的第一页零基页号。 */
    private static final int DEFAULT_PAGE = 0;

    /** 未提供页面大小时采用的统一单页记录数。 */
    private static final int DEFAULT_SIZE = 20;

    /** OpenAPI 契约允许的最大单页记录数，内部查询必须复用该上限。 */
    private static final int MAXIMUM_SIZE = 100;

    /**
     * 在记录实例创建时校验分页参数，防止直接调用构造器绕过 {@link #of(Integer, Integer)} 的参数校验。
     *
     * @param page API 零基页号
     * @param size 每页记录数
     */
    public ApiPageRequest {
        // 零基页号允许为零，不允许出现没有业务语义的负数。
        if (page < 0) {
            throw new InvalidRequestException("INVALID_PAGE", "页号必须大于等于 0");
        }
        // 单页记录数必须与 OpenAPI 的 1 到 100 约束一致，避免内部查询绕开接口上限。
        if (size < 1 || size > MAXIMUM_SIZE) {
            throw new InvalidRequestException("INVALID_PAGE_SIZE", "每页数量必须在 1 到 100 之间");
        }
    }

    /**
     * 根据可空的 HTTP 查询参数创建标准化分页请求，并为缺失参数填充统一默认值。
     *
     * @param page 可为空的 API 零基页号
     * @param size 可为空的每页记录数
     * @return 已完成默认值补齐与边界校验的 API 分页请求
     */
    public static ApiPageRequest of(Integer page, Integer size) {
        // 将 HTTP 层可能缺失的页号转换为统一的 API 默认首页。
        int normalizedPage = page == null ? DEFAULT_PAGE : page;
        // 将 HTTP 层可能缺失的页面大小转换为统一的默认值。
        int normalizedSize = size == null ? DEFAULT_SIZE : size;
        // 复用紧凑构造器，保证工厂方法和直接构造使用相同的校验规则。
        return new ApiPageRequest(normalizedPage, normalizedSize);
    }
}
