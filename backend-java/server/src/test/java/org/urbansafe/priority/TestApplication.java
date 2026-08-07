package org.urbansafe.priority;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "org.urbansafe.priority")
@MapperScan({
    "org.urbansafe.priority.persistence.mapper",
    "org.urbansafe.priority.persistence.mapperext"
})
/** 集成测试专用的最小 Spring Boot 应用配置。 */
public class TestApplication {
}
