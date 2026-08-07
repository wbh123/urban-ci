package org.urbansafe.priority;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan({
    "org.urbansafe.priority.persistence.mapper",
    "org.urbansafe.priority.persistence.mapperext"
})
/** 城安智序 Spring Boot 业务服务启动入口。 */
public class UrbanSafePriorityApplication {

    /**
     * 启动 Spring 容器并装配业务、持久层与安全配置。
     *
     * @param args JVM 传入的命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UrbanSafePriorityApplication.class, args);
    }
}
