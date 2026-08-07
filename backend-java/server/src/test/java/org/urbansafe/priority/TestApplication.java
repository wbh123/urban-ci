package org.urbansafe.priority;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "org.urbansafe.priority",
        // AuditAuthorizationTest 的内嵌 @Configuration 是静态嵌套类，会被组件扫描拾取，导致其
        // mock auditService bean 泄漏进所有 @SpringBootTest 集成上下文，掩盖真实服务行为。
        // 该配置只服务于 AuditAuthorizationTest 自己的轻量上下文（@SpringJUnitConfig 显式导入），
        // 因此在此处按类名排除，避免 mock 静默替换真实 AuditService。
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "org\\.urbansafe\\.priority\\.audit\\.AuditAuthorizationTest\\$MethodSecurityTestConfig"))
@MapperScan({
    "org.urbansafe.priority.persistence.mapper",
    "org.urbansafe.priority.persistence.mapperext"
})
/** 集成测试专用的最小 Spring Boot 应用配置。 */
public class TestApplication {
}
