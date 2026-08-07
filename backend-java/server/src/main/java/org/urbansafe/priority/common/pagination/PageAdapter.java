package org.urbansafe.priority.common.pagination;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/**
 * 集中处理 API 零基分页语义与 MyBatis-Plus 一基分页语义之间的双向适配。
 *
 * <p>业务 Service 不应自行调整页号；所有 {@code page + 1} 与 {@code current - 1}
 * 都只能在本类中执行，以消除跨业务模块的分页偏移差异。
 */
public final class PageAdapter {

    /** API 能够表示的最大零基页号转换为 MyBatis-Plus 一基页号后的最大值。 */
    private static final long MAXIMUM_MYBATIS_CURRENT_PAGE = (long) Integer.MAX_VALUE + 1;

    /** API {@code int} 类型能够表示的最大合法页面大小。 */
    private static final long MAXIMUM_API_PAGE_SIZE = Integer.MAX_VALUE;

    /**
     * 隐藏工具类构造器，防止创建没有状态的分页适配器实例。
     */
    private PageAdapter() {
        // 工具类不保存任何实例状态。
    }

    /**
     * 将 API 的零基分页请求转换为 MyBatis-Plus 所需的一基分页对象。
     *
     * @param pageRequest 已通过边界校验的 API 分页请求
     * @param <T> 后续由 MyBatis-Plus 填充的持久化记录元素类型
     * @return 使用一基当前页和原始页面大小的 MyBatis-Plus 分页对象
     */
    public static <T> Page<T> toMyBatisPage(ApiPageRequest pageRequest) {
        // 明确拒绝空请求，避免在后续计算页号时产生缺少上下文的空指针异常。
        Objects.requireNonNull(pageRequest, "分页请求不能为空");
        // API 第 0 页在这里且仅在这里转换为 MyBatis-Plus 第 1 页。
        long myBatisCurrentPage = (long) pageRequest.page() + 1;
        // 页面大小无需转换，直接保持 API 请求的正整数值。
        long myBatisPageSize = pageRequest.size();
        return new Page<>(myBatisCurrentPage, myBatisPageSize);
    }

    /**
     * 将 MyBatis-Plus 分页查询结果映射为 API 的统一零基分页响应。
     *
     * @param myBatisPage MyBatis-Plus 返回的一基分页查询结果
     * @param recordMapper 将持久化记录转换为 API 记录的函数
     * @param <T> MyBatis-Plus 当前页原始记录元素类型
     * @param <R> API 当前页转换后记录元素类型
     * @return 使用零基页号、转换后记录列表与完整分页元数据的 API 响应
     */
    public static <T, R> PageResult<R> toApiPage(
            IPage<T> myBatisPage, Function<T, R> recordMapper) {
        // 明确验证依赖输入，确保调用方可以立即定位分页结果或转换函数缺失的问题。
        Objects.requireNonNull(myBatisPage, "MyBatis-Plus 分页结果不能为空");
        Objects.requireNonNull(recordMapper, "分页记录转换函数不能为空");
        // 先读取 MyBatis-Plus 一基当前页，保证后续任何减一操作都使用已经验证的合法值。
        long myBatisCurrentPage = myBatisPage.getCurrent();
        // 当前页必须能还原为 API 的非负 int 零基页号，避免负页或 long 下溢泄漏为算术异常。
        if (myBatisCurrentPage < 1 || myBatisCurrentPage > MAXIMUM_MYBATIS_CURRENT_PAGE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE", "MyBatis-Plus 当前页必须在 1 到 2147483648 之间");
        }
        // 先读取 MyBatis-Plus 页面大小，保证后续窄化为 int 前已经完成范围校验。
        long myBatisPageSize = myBatisPage.getSize();
        // 页面大小必须是 API int 可表达的正数，避免零、负数或窄化溢出产生错误元数据。
        if (myBatisPageSize < 1 || myBatisPageSize > MAXIMUM_API_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_SIZE", "MyBatis-Plus 页面大小必须在 1 到 2147483647 之间");
        }
        // 将当前页的每条持久化记录转换为对外 API 记录，并生成不可变结果列表。
        List<R> content = myBatisPage.getRecords().stream().map(recordMapper).toList();
        // MyBatis-Plus 当前页从 1 开始，此处且仅此处还原为 API 的零基页号。
        int apiPage = Math.toIntExact(myBatisCurrentPage - 1);
        // 请求页面大小来源于 MyBatis-Plus 分页对象，转换为 API 使用的 int 类型。
        int apiSize = Math.toIntExact(myBatisPageSize);
        // 总记录数无需页号转换，直接作为 API 总记录数返回。
        long totalElements = myBatisPage.getTotal();
        // 总页数由 MyBatis-Plus 计算，保留 long 以避免大数据量查询时溢出。
        long totalPages = myBatisPage.getPages();
        return new PageResult<>(content, apiPage, apiSize, totalElements, totalPages);
    }
}
