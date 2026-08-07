package org.urbansafe.priority.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.urbansafe.priority.audit.controller.AuditOperationController;
import org.urbansafe.priority.audit.result.OperationLogPageResult;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.model.api.AuditOperationApi;

/**
 * 审计 Controller 方法级授权测试；直接调用 Spring 代理，不依赖 Docker、数据库或额外测试依赖。
 */
@SpringJUnitConfig(AuditAuthorizationTest.MethodSecurityTestConfig.class)
class AuditAuthorizationTest {

    @Autowired
    private AuditOperationApi auditOperationController;

    @Autowired
    private AuditService auditService;

    /** 为 ADMIN 成功场景准备空分页结果，并清理认证上下文。 */
    @BeforeEach
    void prepareAuditResult() {
        SecurityContextHolder.clearContext();
        OperationLogPageResult pageResult = new OperationLogPageResult(List.of(), 0, 20, 0L, 0L);
        when(auditService.page(isNull(), isNull(), isNull(), isNull(), any(ApiPageRequest.class)))
                .thenReturn(pageResult);
    }

    /** 每个用例后清理线程认证，避免测试间角色泄漏。 */
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /** 未认证调用会被方法安全拒绝；Web 安全入口将该异常映射为 401。 */
    @Test
    void unauthenticatedRequestShouldBeRejected() {
        assertThatThrownBy(() -> auditOperationController.listOperationLogs(
                null, null, null, null, 0, 20))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    /** 普通角色会被 ADMIN 规则拒绝；Web 安全入口将该异常映射为 403。 */
    @Test
    void normalUserShouldBeRejected() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("inspector", null, "ROLE_INSPECTOR"));

        assertThatThrownBy(() -> auditOperationController.listOperationLogs(
                null, null, null, null, 0, 20))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** ADMIN 角色能够通过方法级权限并得到 HTTP 200 Controller 响应。 */
    @Test
    void adminShouldReturn200() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));

        assertThat(auditOperationController.listOperationLogs(
                null, null, null, null, 0, 20).getStatusCode().value())
                .isEqualTo(200);
    }

    /** 验证 Controller 将 API 第 0 页交给统一分页请求，并返回保持零基的分页元数据。 */
    @Test
    void adminPageZeroShouldUseApiPageRequestAndReturnZeroBasedPage() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("admin", null, "ROLE_ADMIN"));
        OperationLogPageResult pageResult = new OperationLogPageResult(List.of(), 0, 20, 0L, 0L);
        when(auditService.page(isNull(), isNull(), isNull(), isNull(), eq(new ApiPageRequest(0, 20))))
                .thenReturn(pageResult);

        var response = auditOperationController.listOperationLogs(null, null, null, null, 0, 20);

        verify(auditService).page(isNull(), isNull(), isNull(), isNull(), eq(new ApiPageRequest(0, 20)));
        assertThat(response.getBody().getData().getPage().getPage()).isEqualTo(0);
    }

    /** 验证审计 Service 接收统一零基分页请求，而不是分散的整数分页参数。 */
    @Test
    void auditPageContractShouldAcceptUnifiedZeroBasedPageRequest() {
        assertThat(java.util.Arrays.stream(AuditService.class.getMethods())
                .filter(method -> method.getName().equals("page"))
                .anyMatch(method -> method.getParameterCount() == 5
                        && method.getParameterTypes()[4].getName().equals(
                                "org.urbansafe.priority.common.pagination.ApiPageRequest")
                        && method.getReturnType().getName().equals(
                                "org.urbansafe.priority.audit.result.OperationLogPageResult")))
                .isTrue();
    }

    /** 构造只包含方法安全代理、Controller 和 Mock Service 的轻量测试上下文。 */
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        /** 提供统一审计 Service Mock。 */
        @Bean
        AuditService auditService() {
            return mock(AuditService.class);
        }

        /** 提供待验证的审计 Controller。 */
        @Bean
        AuditOperationController auditOperationController(AuditService auditService) {
            return new AuditOperationController(auditService);
        }
    }
}
