package org.urbansafe.priority.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.BusinessException;
import org.urbansafe.priority.common.exception.InvalidRequestException;

/**
 * 验证统一分页适配器在接口零基页号与 MyBatis-Plus 一基页号之间的转换约定。
 *
 * <p>测试直接使用 MyBatis-Plus 的真实 {@link Page} 对象，避免通过 Mock 掩盖分页元数据转换错误。
 */
class PageAdapterTest {

    /**
     * 验证接口侧第零页转换为 MyBatis-Plus 第一页，并将查询结果的页号还原为接口侧零基页号。
     */
    @Test
    void 应将零基接口页转换为一基MyBatis页并还原响应页号() {
        // 验证 API 的第 0 页仅在适配器边界转换为 MyBatis-Plus 的第 1 页。
        assertThat(PageAdapter.toMyBatisPage(ApiPageRequest.of(0, 1)).getCurrent()).isEqualTo(1L);

        // 构造代表 MyBatis-Plus 第 3 页的真实分页查询结果。
        Page<String> myBatisPage = new Page<>(3, 1, 3);
        myBatisPage.setRecords(List.of("third"));

        // 执行结果映射并验证 API 分页响应的全部元数据均来自分页查询结果。
        PageResult<String> pageResult = PageAdapter.toApiPage(myBatisPage, Function.identity());
        assertThat(pageResult.content()).containsExactly("third");
        assertThat(pageResult.page()).isEqualTo(2);
        assertThat(pageResult.size()).isEqualTo(1);
        assertThat(pageResult.totalElements()).isEqualTo(3L);
        assertThat(pageResult.totalPages()).isEqualTo(3);
    }

    /**
     * 验证未提供分页参数时使用统一默认值，避免各 Controller 各自定义默认分页行为。
     */
    @Test
    void 应在未提供分页参数时使用零页和二十条的默认值() {
        // 传入空包装类型参数，模拟 HTTP 查询参数未提供的场景。
        ApiPageRequest pageRequest = ApiPageRequest.of(null, null);

        // 默认页号保持 API 约定的零基语义。
        assertThat(pageRequest.page()).isZero();
        // 默认页面大小固定为 20 条。
        assertThat(pageRequest.size()).isEqualTo(20);
    }

    /**
     * 验证分页边界参数不合法时使用统一的请求参数异常进行拒绝。
     */
    @Test
    void 应按OpenApi范围校验页面大小() {
        assertThat(ApiPageRequest.of(0, 1).size()).isEqualTo(1);
        assertThat(ApiPageRequest.of(0, 20).size()).isEqualTo(20);
        assertThat(ApiPageRequest.of(0, 100).size()).isEqualTo(100);
        // API 页号为零基，因此负数无任何合法语义。
        assertThatThrownBy(() -> ApiPageRequest.of(-1, 1))
                .isInstanceOf(InvalidRequestException.class)
                // 错误码是前端和调用方稳定识别页号错误的契约，不可随实现细节改变。
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE");
        // 每页数量必须至少为一条，零会造成无意义的查询。
        assertThatThrownBy(() -> ApiPageRequest.of(0, 0))
                .isInstanceOf(InvalidRequestException.class)
                // 错误码是前端和调用方稳定识别页面大小错误的契约，不可随实现细节改变。
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE_SIZE");
        assertThatThrownBy(() -> ApiPageRequest.of(0, -1))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE_SIZE");
        assertThatThrownBy(() -> ApiPageRequest.of(0, 101))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE_SIZE");
    }

    /**
     * 验证非法的 MyBatis-Plus 当前页不能被转换为负的 API 零基页号，并且使用稳定错误码。
     */
    @Test
    void 应拒绝零页和长整型最小值的MyBatis当前页() {
        // 构造违反 MyBatis-Plus 一基约定的第零页，防止其被还原为 API 的负一页。
        Page<String> zeroCurrentPage = new Page<>(1, 1, 0);
        // 在对象构造完成后覆盖当前页，绕开 MyBatis-Plus 构造器对非法初值的默认化处理。
        zeroCurrentPage.setCurrent(0);
        // 构造最小 long 页号，验证校验发生在减一之前，避免 long 下溢为非业务异常。
        Page<String> minimumCurrentPage = new Page<>(1, 1, 0);
        // 显式设置极端页号，模拟第三方分页实现或异常结果传入公开适配器方法的场景。
        minimumCurrentPage.setCurrent(Long.MIN_VALUE);

        // 非法当前页应统一以可预期的请求参数错误码返回给接口调用方。
        assertThatThrownBy(() -> PageAdapter.toApiPage(zeroCurrentPage, Function.identity()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE");
        // 长整型下界同样必须使用业务错误码，而不是泄漏 ArithmeticException。
        assertThatThrownBy(() -> PageAdapter.toApiPage(minimumCurrentPage, Function.identity()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE");
    }

    /**
     * 验证可表示的最大 MyBatis-Plus 当前页还原为 API 最大 int 页号，并拒绝非法页面大小。
     */
    @Test
    void 应还原最大接口页号并拒绝超出接口范围的MyBatis元数据() {
        // API 最大 int 页号加一后仍是合法的 MyBatis-Plus 一基页号，必须准确还原。
        long maximumMyBatisCurrentPage = (long) Integer.MAX_VALUE + 1;
        // 使用合法页面大小构造边界分页结果，验证窄化转换不会丢失页号语义。
        Page<String> maximumCurrentPage = new Page<>(maximumMyBatisCurrentPage, 1, 0);
        // 执行反向转换并保留结果，供后续断言最大 API 页号。
        PageResult<String> pageResult = PageAdapter.toApiPage(maximumCurrentPage, Function.identity());
        // 一基最大页应还原为零基的 int 最大值，且绝不能变成负数。
        assertThat(pageResult.page()).isEqualTo(Integer.MAX_VALUE);

        // 构造超过 API 可还原范围的当前页，验证不能发生负数或错误的窄化转换。
        Page<String> excessiveCurrentPage = new Page<>(1, 1, 0);
        // 最大合法一基页再加一应被稳定拒绝。
        excessiveCurrentPage.setCurrent(maximumMyBatisCurrentPage + 1);
        assertThatThrownBy(() -> PageAdapter.toApiPage(excessiveCurrentPage, Function.identity()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE");

        // 构造零页面大小，验证输出侧元数据也使用稳定的页面大小错误码。
        Page<String> zeroSizePage = new Page<>(1, 1, 0);
        // 在对象构造完成后覆盖页面大小，确保验证的是适配器而非 Page 构造器的默认行为。
        zeroSizePage.setSize(0);
        assertThatThrownBy(() -> PageAdapter.toApiPage(zeroSizePage, Function.identity()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE_SIZE");

        // 构造超过 API int 表示范围的页面大小，验证窄化前的上界校验。
        Page<String> excessiveSizePage = new Page<>(1, 1, 0);
        // API int 最大值再加一必须被拒绝，不能在转换后变成负数或截断值。
        excessiveSizePage.setSize((long) Integer.MAX_VALUE + 1);
        assertThatThrownBy(() -> PageAdapter.toApiPage(excessiveSizePage, Function.identity()))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(throwable -> ((BusinessException) throwable).getErrorCode())
                .isEqualTo("INVALID_PAGE_SIZE");
    }
}
